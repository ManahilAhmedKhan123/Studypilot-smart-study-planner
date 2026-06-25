/* ============================================================
   StudyPilot — Smart Student Study Planner (Vanilla JS)
   Tech: HTML5 + CSS3 + Bootstrap 5 + Vanilla JavaScript
   Runs by opening index.html (no backend)
   ============================================================ */

'use strict';

// ----------------------------
// Local storage + State
// ----------------------------
const STORAGE_KEY = 'studypilot_state_v1';

const DIFF = {
  easy:   { label: 'Easy',      mult: 1.0 },
  medium: { label: 'Medium',    mult: 1.35 },
  hard:   { label: 'Hard',      mult: 1.75 },
  vhard:  { label: 'Very Hard', mult: 2.15 },
};

let state = {
  theme: 'light',
  selectedDifficulty: 'medium',
  subjects: [], // {id,name,emoji,difficulty,examDate,topics:[{text,done}],createdAt}
  tasks: [],    // {id,text,done,createdAt}
  timer: { sessionsToday: 0, focusMinsTotal: 0, streak: 0, lastStreakDate: null },
  lastPlan: null,
};

function saveState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function loadState() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return;
  try {
    const parsed = JSON.parse(raw);
    state = { ...state, ...parsed };
  } catch (_) {}
}

// ----------------------------
// Quotes
// ----------------------------
const QUOTES = [
  { text: 'The secret of getting ahead is getting started.', author: 'Mark Twain' },
  { text: 'Success is the sum of small efforts repeated day in and day out.', author: 'Robert Collier' },
  { text: 'Don’t watch the clock; do what it does. Keep going.', author: 'Sam Levenson' },
  { text: 'It always seems impossible until it’s done.', author: 'Nelson Mandela' },
  { text: 'The expert in anything was once a beginner.', author: 'Helen Hayes' },
  { text: 'Small progress is still progress.', author: 'Unknown' },
  { text: 'Focus on being productive instead of busy.', author: 'Unknown' },
  { text: 'Discipline beats motivation when motivation fades.', author: 'Unknown' },
  { text: 'Your future self is watching.', author: 'Unknown' },
  { text: 'Learn it once. Use it forever.', author: 'Unknown' },
];

function randomQuote() {
  return QUOTES[Math.floor(Math.random() * QUOTES.length)];
}

// ----------------------------
// Init
// ----------------------------
document.addEventListener('DOMContentLoaded', () => {
  loadState();
  applyTheme(state.theme);
  hydrateHeader();
  renderAll();

  // Rotate dashboard quote
  setInterval(() => {
    const q = randomQuote();
    setText('dashQuoteText', q.text);
    setText('dashQuoteAuthor', '— ' + q.author);
  }, 30000);

  // Enter adds task
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    const el = document.activeElement;
    if (el && el.id === 'taskInput') addTask();
  });
});

function renderAll() {
  renderDashboard();
  renderSubjects();
  renderTasks();
  renderQuotes();
  renderTimerStats();
}

// ----------------------------
// Navigation + Sidebar
// ----------------------------
const PAGE_TITLES = {
  dashboard: 'Dashboard',
  subjects: 'Subjects',
  planner: 'Exam Planner',
  timer: 'Study Timer',
  tasks: 'Tasks',
  quotes: 'Motivation',
};

let sidebarOverlay = null;

function navigate(section, el) {
  document.querySelectorAll('.page-section').forEach(s => s.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));

  document.getElementById('sec-' + section)?.classList.add('active');
  el?.classList.add('active');

  setText('pageTitle', PAGE_TITLES[section] || 'StudyPilot');

  if (window.innerWidth < 992) closeSidebar();

  if (section === 'dashboard') renderDashboard();
  if (section === 'subjects') renderSubjects();
  if (section === 'tasks') renderTasks();
}

function toggleSidebar() {
  const sb = document.getElementById('sidebar');
  const open = sb.classList.toggle('open');
  if (open) {
    if (!sidebarOverlay) {
      sidebarOverlay = document.createElement('div');
      sidebarOverlay.className = 'sidebar-overlay show';
      sidebarOverlay.addEventListener('click', closeSidebar);
      document.body.appendChild(sidebarOverlay);
    } else {
      sidebarOverlay.classList.add('show');
    }
  } else {
    closeSidebar();
  }
}

function closeSidebar() {
  document.getElementById('sidebar')?.classList.remove('open');
  sidebarOverlay?.classList.remove('show');
}

// ----------------------------
// Theme
// ----------------------------
function toggleTheme() {
  state.theme = state.theme === 'dark' ? 'light' : 'dark';
  applyTheme(state.theme);
  saveState();
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  const icon = document.getElementById('themeIcon');
  const label = document.getElementById('themeLabel');
  if (!icon || !label) return;

  if (theme === 'dark') {
    icon.className = 'bi bi-sun-fill';
    label.textContent = 'Light Mode';
  } else {
    icon.className = 'bi bi-moon-stars-fill';
    label.textContent = 'Dark Mode';
  }
}

// ----------------------------
// Header + Welcome
// ----------------------------
function hydrateHeader() {
  const now = new Date();
  const h = now.getHours();
  const greet = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  setText('welcomeMsg', greet + '!');
  setText('todayDate', now.toLocaleDateString('en-US', { weekday:'long', month:'long', day:'numeric', year:'numeric' }));
  setText('streakCount', String(state.timer.streak || 0));
}

// ----------------------------
// Dashboard
// ----------------------------
function renderDashboard() {
  setText('statSubjects', String(state.subjects.length));
  setText('statTasksDone', String(state.tasks.filter(t => t.done).length));
  setText('statFocusTime', formatMins(state.timer.focusMinsTotal || 0));

  const upcomingExams = state.subjects.filter(s => s.examDate && daysUntil(s.examDate) >= 0).length;
  setText('statExams', String(upcomingExams));

  // Summary
  const dailySummary = document.getElementById('dailySummary');
  if (dailySummary) {
    const topicsLeft = state.subjects.reduce((acc, s) => acc + s.topics.filter(t => !t.done).length, 0);
    const nextExam = getNextExamSubject();
    const examLine = nextExam
      ? `${escapeHtml(nextExam.emoji)} ${escapeHtml(nextExam.name)} in <strong>${daysUntil(nextExam.examDate)} days</strong>`
      : 'No exams set yet. Add an exam date to a subject.';

    dailySummary.innerHTML = [
      `<div class="summary-row"><i class="bi bi-bullseye"></i><div><strong>${topicsLeft}</strong> topics remaining across all subjects</div></div>`,
      `<div class="summary-row"><i class="bi bi-alarm"></i><div>${examLine}</div></div>`,
      `<div class="summary-row"><i class="bi bi-check2-square"></i><div><strong>${state.tasks.length}</strong> tasks in your list</div></div>`,
    ].join('');
  }

  // Progress overview
  const po = document.getElementById('progressOverview');
  if (po) {
    if (!state.subjects.length) {
      po.innerHTML = `<div class="text-muted">Add subjects to see progress.</div>`;
    } else {
      po.innerHTML = state.subjects
        .slice()
        .sort((a,b) => subjectCompletion(b) - subjectCompletion(a))
        .slice(0, 4)
        .map(s => {
          const pct = subjectCompletion(s);
          return `
            <div class="progress-ring-item">
              <div class="progress-meta">
                <span>${escapeHtml(s.emoji)} ${escapeHtml(s.name)}</span>
                <span>${pct}%</span>
              </div>
              <div class="progress-track"><div class="progress-fill" style="width:${pct}%"></div></div>
            </div>`;
        })
        .join('');
    }
  }

  renderExamReminders();

  const q = randomQuote();
  setText('dashQuoteText', q.text);
  setText('dashQuoteAuthor', '— ' + q.author);
}

function renderExamReminders() {
  const el = document.getElementById('examReminders');
  if (!el) return;

  const exams = state.subjects
    .filter(s => s.examDate && daysUntil(s.examDate) >= 0)
    .sort((a,b) => daysUntil(a.examDate) - daysUntil(b.examDate))
    .slice(0, 6);

  if (!exams.length) {
    el.innerHTML = `<div class="text-muted">No exams scheduled yet.</div>`;
    return;
  }

  el.innerHTML = exams.map(s => {
    const days = daysUntil(s.examDate);
    const cls = days <= 3 ? 'urgent' : days <= 7 ? 'soon' : '';
    const badge = days === 0 ? 'Today' : `${days}d`;
    return `
      <div class="exam-card ${cls}">
        <div class="exam-days">${badge}</div>
        <div>
          <h4>${escapeHtml(s.emoji)} ${escapeHtml(s.name)}</h4>
          <small>${formatDate(s.examDate)} • <span class="diff-badge diff-${s.difficulty}">${DIFF[s.difficulty]?.label || s.difficulty}</span></small>
        </div>
      </div>`;
  }).join('');
}

function getNextExamSubject() {
  const list = state.subjects
    .filter(s => s.examDate && daysUntil(s.examDate) >= 0)
    .sort((a,b) => daysUntil(a.examDate) - daysUntil(b.examDate));
  return list[0] || null;
}

// ----------------------------
// Subjects + Topics
// ----------------------------
function selectDifficulty(diff, btn) {
  state.selectedDifficulty = diff;
  document.querySelectorAll('#difficultyBtns .diff-btn').forEach(b => b.classList.remove('active'));
  btn?.classList.add('active');
}

function saveSubject() {
  const name = (document.getElementById('subName')?.value || '').trim();
  if (!name) return toast('Please enter a subject name.', 'warning');

  const emoji = (document.getElementById('subEmoji')?.value || '').trim() || '📘';
  const examDate = document.getElementById('subExamDate')?.value || '';
  const topicsRaw = (document.getElementById('subTopics')?.value || '').trim();
  const topics = topicsRaw
    ? topicsRaw.split('\n').map(t => t.trim()).filter(Boolean).map(t => ({ text: t, done: false }))
    : [];

  state.subjects.unshift({
    id: uid(),
    name,
    emoji,
    difficulty: state.selectedDifficulty || 'medium',
    examDate,
    topics,
    createdAt: Date.now(),
  });

  saveState();
  renderSubjects();
  renderDashboard();
  toast('Subject added.', 'success');

  // Reset modal fields
  document.getElementById('subName').value = '';
  document.getElementById('subEmoji').value = '';
  document.getElementById('subExamDate').value = '';
  document.getElementById('subTopics').value = '';
  selectDifficulty('medium', document.querySelector('#difficultyBtns .diff-btn[data-diff="medium"]'));

  bootstrap.Modal.getInstance(document.getElementById('subjectModal'))?.hide();
}

function renderSubjects() {
  const grid = document.getElementById('subjectGrid');
  const empty = document.getElementById('subjectsEmpty');
  if (!grid || !empty) return;

  grid.querySelectorAll('.subject-col').forEach(n => n.remove());

  if (!state.subjects.length) {
    empty.style.display = '';
    return;
  }
  empty.style.display = 'none';

  state.subjects.forEach(s => {
    const col = document.createElement('div');
    col.className = 'col-sm-6 col-xl-4 subject-col';

    const done = s.topics.filter(t => t.done).length;
    const total = s.topics.length;
    const pct = total ? Math.round((done / total) * 100) : 0;
    const examLine = s.examDate
      ? `${formatDate(s.examDate)} • ${daysUntil(s.examDate) >= 0 ? daysUntil(s.examDate) + ' days left' : 'Past'}`
      : 'No exam date';

    col.innerHTML = `
      <div class="subject-card" onclick="openSubjectDetail('${s.id}')">
        <div class="subject-top">
          <div class="subject-emoji">${escapeHtml(s.emoji)}</div>
          <span class="diff-badge diff-${s.difficulty}">${DIFF[s.difficulty]?.label || s.difficulty}</span>
        </div>
        <h4>${escapeHtml(s.name)}</h4>
        <div class="subject-meta"><i class="bi bi-calendar3"></i> ${examLine}</div>
        <div class="progress-meta"><span>Progress</span><span>${pct}%</span></div>
        <div class="progress-track"><div class="progress-fill" style="width:${pct}%"></div></div>
        <div class="subject-actions" onclick="event.stopPropagation()">
          <button type="button" class="btn-icon-only neutral" title="Details" onclick="openSubjectDetail('${s.id}')"><i class="bi bi-eye"></i></button>
          <button type="button" class="btn-icon-only" title="Delete" onclick="deleteSubject('${s.id}')"><i class="bi bi-trash3"></i></button>
        </div>
      </div>`;

    grid.appendChild(col);
  });
}

function deleteSubject(id) {
  if (!confirm('Delete this subject and its topics?')) return;
  state.subjects = state.subjects.filter(s => s.id !== id);
  saveState();
  renderSubjects();
  renderDashboard();
  toast('Subject deleted.', 'info');
}

function openSubjectDetail(id) {
  const s = state.subjects.find(x => x.id === id);
  if (!s) return;

  const title = document.getElementById('subjectDetailTitle');
  const body = document.getElementById('subjectDetailBody');
  if (!title || !body) return;

  const done = s.topics.filter(t => t.done).length;
  const total = s.topics.length;
  const pct = total ? Math.round((done / total) * 100) : 0;

  title.textContent = `${s.emoji} ${s.name}`;

  body.innerHTML = `
    <div class="row g-3 mb-3">
      <div class="col-4"><div class="planner-stat"><div class="num">${total}</div><div class="lbl">Topics</div></div></div>
      <div class="col-4"><div class="planner-stat"><div class="num">${done}</div><div class="lbl">Done</div></div></div>
      <div class="col-4"><div class="planner-stat"><div class="num">${pct}%</div><div class="lbl">Progress</div></div></div>
    </div>
    <div class="progress-track mb-3"><div class="progress-fill" style="width:${pct}%"></div></div>
    <div class="d-flex flex-wrap gap-2 mb-3">
      <span class="diff-badge diff-${s.difficulty}">${DIFF[s.difficulty]?.label || s.difficulty}</span>
      ${s.examDate ? `<span class="kpi-pill"><strong>Exam</strong> ${formatDate(s.examDate)}</span>` : ''}
      <span class="kpi-pill"><strong>Remaining</strong> ${s.topics.filter(t => !t.done).length}</span>
    </div>

    <h6 class="mb-2" style="font-weight:800;">Syllabus / Topics</h6>
    <div>
      ${total ? s.topics.map((t, idx) => {
        const rowCls = t.done ? 'topic-row done' : 'topic-row';
        const chkCls = t.done ? 'topic-check done' : 'topic-check';
        const icon = t.done ? '<i class="bi bi-check-lg"></i>' : '';
        return `
          <div class="${rowCls}">
            <div class="${chkCls}" onclick="toggleTopic('${s.id}', ${idx})" title="Toggle">${icon}</div>
            <span>${escapeHtml(t.text)}</span>
          </div>`;
      }).join('') : `<div class="text-muted">No topics added yet.</div>`}
    </div>
  `;

  new bootstrap.Modal(document.getElementById('subjectDetailModal')).show();
}

function toggleTopic(subjectId, topicIndex) {
  const s = state.subjects.find(x => x.id === subjectId);
  if (!s || !s.topics[topicIndex]) return;
  s.topics[topicIndex].done = !s.topics[topicIndex].done;
  saveState();
  renderSubjects();
  renderDashboard();
  openSubjectDetail(subjectId);
}

function subjectCompletion(subject) {
  const total = subject.topics.length;
  if (!total) return 0;
  const done = subject.topics.filter(t => t.done).length;
  return Math.round((done / total) * 100);
}

// ----------------------------
// Smart Exam Planner (multi-subject)
// ----------------------------
function generateSmartPlan() {
  if (!state.subjects.length) return toast('Add at least one subject first.', 'warning');

  const examDate = document.getElementById('globalExamDate')?.value || '';
  const hoursPerDay = parseFloat(document.getElementById('dailyStudyHours')?.value || '0');
  const bufferDays = parseInt(document.getElementById('revisionBuffer')?.value || '0', 10);
  const baseMinsPerTopic = parseInt(document.getElementById('minutesPerTopic')?.value || '45', 10);

  if (!examDate) return toast('Please select a target exam date.', 'warning');
  if (!isFutureDate(examDate)) return toast('Exam date must be in the future.', 'warning');
  if (!hoursPerDay || hoursPerDay < 0.5) return toast('Study hours/day must be at least 0.5.', 'warning');
  if (!baseMinsPerTopic || baseMinsPerTopic < 15) return toast('Minutes per topic should be at least 15.', 'warning');

  const subjects = state.subjects
    .map(s => {
      const remaining = s.topics.filter(t => !t.done).length;
      const diffMult = DIFF[s.difficulty]?.mult || 1.35;
      return {
        id: s.id,
        name: s.name,
        emoji: s.emoji,
        difficulty: s.difficulty,
        diffMult,
        remainingTopics: remaining,
      };
    })
    .filter(s => s.remainingTopics > 0);

  if (!subjects.length) return toast('All topics are completed. Add more topics to plan.', 'info');

  const totalDays = daysUntil(examDate);
  const studyDays = Math.max(1, totalDays - Math.max(0, bufferDays));

  const totalWeight = subjects.reduce((sum, s) => sum + (s.remainingTopics * s.diffMult), 0);

  const allocations = subjects.map(s => {
    const weight = s.remainingTopics * s.diffMult;
    const share = weight / totalWeight;
    const hours = roundToQuarter(hoursPerDay * share);

    // Adjust minutes per topic for difficulty (harder topics take longer)
    const minsPerTopicAdj = Math.round(baseMinsPerTopic * (0.9 + (s.diffMult - 1) * 0.35));
    const dailyTopicsTarget = Math.max(0, Math.floor(((hours * 60) / minsPerTopicAdj) + 0.0001));
    const estDaysNeeded = dailyTopicsTarget > 0 ? Math.ceil(s.remainingTopics / dailyTopicsTarget) : Infinity;

    return {
      ...s,
      weight,
      share,
      hoursPerDay: hours,
      minsPerTopicAdj,
      dailyTopicsTarget,
      estDaysNeeded,
    };
  });

  normalizeAllocationHours(allocations, hoursPerDay);

  const priorities = allocations
    .map(a => ({
      ...a,
      risk: a.estDaysNeeded === Infinity ? 1e9 : (a.estDaysNeeded / studyDays),
    }))
    .sort((a,b) => b.risk - a.risk);

  const schedule = buildSchedule(examDate, studyDays, allocations);

  renderPlannerResults({ examDate, hoursPerDay, bufferDays, baseMinsPerTopic, totalDays, studyDays, allocations, priorities, schedule });
  toast('Smart plan generated.', 'success');
}

function generateQuickPlan() {
  const examDate = document.getElementById('globalExamDate')?.value || '';
  const hoursPerDay = parseFloat(document.getElementById('dailyStudyHours')?.value || '0');
  const bufferDays = parseInt(document.getElementById('revisionBuffer')?.value || '0', 10);
  const baseMinsPerTopic = parseInt(document.getElementById('minutesPerTopic')?.value || '45', 10);

  const name = (document.getElementById('quickSubjectName')?.value || '').trim() || 'Subject';
  const topicsLeft = parseInt(document.getElementById('quickTopicsLeft')?.value || '0', 10);
  const diff = document.getElementById('quickDifficulty')?.value || 'medium';

  if (!examDate) return toast('Select a target exam date first.', 'warning');
  if (!isFutureDate(examDate)) return toast('Exam date must be in the future.', 'warning');
  if (!topicsLeft || topicsLeft < 1) return toast('Enter topics remaining (>= 1).', 'warning');
  if (!hoursPerDay || hoursPerDay < 0.5) return toast('Study hours/day must be at least 0.5.', 'warning');

  const totalDays = daysUntil(examDate);
  const studyDays = Math.max(1, totalDays - Math.max(0, bufferDays));
  const diffMult = DIFF[diff]?.mult || 1.35;
  const minsPerTopicAdj = Math.round(baseMinsPerTopic * (0.9 + (diffMult - 1) * 0.35));

  const totalMinsNeeded = topicsLeft * minsPerTopicAdj;
  const minsPerDayNeeded = Math.ceil(totalMinsNeeded / studyDays);
  const hoursPerDayNeeded = Math.max(0.5, roundToQuarter(minsPerDayNeeded / 60));
  const topicPerDay = Math.max(1, Math.floor((hoursPerDay * 60) / minsPerTopicAdj));

  toast(`${name}: Aim for ~${topicPerDay} topics/day (≈ ${hoursPerDayNeeded.toFixed(2)}h/day needed).`, 'info');
}

function renderPlannerResults(res) {
  document.getElementById('plannerEmpty')?.classList.add('d-none');
  document.getElementById('plannerResults')?.classList.remove('d-none');

  const stats = document.getElementById('plannerStats');
  if (stats) {
    stats.innerHTML = `
      <div class="col-6 col-xl-3"><div class="planner-stat"><div class="num">${res.totalDays}</div><div class="lbl">Days Left</div></div></div>
      <div class="col-6 col-xl-3"><div class="planner-stat"><div class="num">${res.studyDays}</div><div class="lbl">Study Days</div></div></div>
      <div class="col-6 col-xl-3"><div class="planner-stat"><div class="num">${res.bufferDays}</div><div class="lbl">Buffer Days</div></div></div>
      <div class="col-6 col-xl-3"><div class="planner-stat"><div class="num">${res.hoursPerDay.toFixed(1)}h</div><div class="lbl">Hours / Day</div></div></div>
    `;
  }

  const allocEl = document.getElementById('subjectAllocation');
  if (allocEl) {
    const maxH = Math.max(...res.allocations.map(a => a.hoursPerDay));
    allocEl.innerHTML = res.allocations
      .slice()
      .sort((a,b) => b.hoursPerDay - a.hoursPerDay)
      .map(a => {
        const w = maxH ? Math.round((a.hoursPerDay / maxH) * 100) : 0;
        return `
          <div class="allocation-row">
            <div class="alloc-info">
              <strong>${escapeHtml(a.emoji)} ${escapeHtml(a.name)}</strong>
              <small><span class="diff-badge diff-${a.difficulty}">${DIFF[a.difficulty]?.label || a.difficulty}</span></small>
              <div class="alloc-kpis">
                <span class="kpi-pill"><strong>${a.remainingTopics}</strong> left</span>
                <span class="kpi-pill"><strong>${a.dailyTopicsTarget}</strong>/day</span>
              </div>
            </div>
            <div class="alloc-bar-wrap">
              <div class="alloc-bar"><span style="width:${w}%"></span></div>
            </div>
            <div class="alloc-hours">${a.hoursPerDay.toFixed(2)}h</div>
          </div>
        `;
      })
      .join('');
  }

  const pri = document.getElementById('attentionPriority');
  if (pri) {
    pri.innerHTML = res.priorities.slice(0, 4).map((p, idx) => {
      const high = idx === 0 || p.risk >= 1.05;
      const cls = high ? 'priority-item high' : 'priority-item';
      const reason = p.estDaysNeeded === Infinity
        ? 'No daily topic target (increase hours/day or lower minutes/topic).'
        : (p.risk >= 1.05
            ? `At this pace, you may need ~${p.estDaysNeeded} days (you have ${res.studyDays}).`
            : `On track: ~${p.estDaysNeeded} days needed (you have ${res.studyDays}).`);
      return `
        <div class="${cls}">
          <div class="priority-rank">${idx + 1}</div>
          <div>
            <div style="font-weight:800;">${escapeHtml(p.emoji)} ${escapeHtml(p.name)}</div>
            <div class="text-muted small">${reason}</div>
          </div>
        </div>`;
    }).join('');
  }

  const tbody = document.getElementById('scheduleTableBody');
  if (tbody) {
    tbody.innerHTML = res.schedule.slice(0, 30).map((d, idx) => `
      <tr>
        <td><strong>Day ${idx + 1}</strong></td>
        <td>${d.dateLabel}</td>
        <td>${d.focusSubjects.map(s => escapeHtml(s.name)).join('<br>')}</td>
        <td>${d.topicTargets.join('<br>')}</td>
        <td>${d.hours.toFixed(1)}h</td>
      </tr>
    `).join('');
  }

  const recs = document.getElementById('recommendationsList');
  if (recs) {
    recs.innerHTML = buildRecommendations(res)
      .map(r => `<li><i class="bi ${r.icon}"></i><span>${r.text}</span></li>`)
      .join('');
  }

  state.lastPlan = {
    examDate: res.examDate,
    hoursPerDay: res.hoursPerDay,
    studyDays: res.studyDays,
    allocations: res.allocations.map(a => ({ id: a.id, name: a.name, emoji: a.emoji, hoursPerDay: a.hoursPerDay })),
  };
  saveState();
}

function buildSchedule(examDate, studyDays, allocations) {
  const rem = allocations.map(a => ({ ...a, remaining: a.remainingTopics }));
  const schedule = [];

  const today = new Date(); today.setHours(0,0,0,0);
  for (let d = 0; d < studyDays; d++) {
    rem.sort((a,b) => (b.remaining * b.diffMult) - (a.remaining * a.diffMult));
    const focus = rem.filter(x => x.remaining > 0).slice(0, 2);

    const topicTargets = [];
    focus.forEach(s => {
      const t = Math.min(s.remaining, Math.max(1, s.dailyTopicsTarget || 1));
      s.remaining -= t;
      topicTargets.push(`${t} topics`);
    });

    const leftAll = rem.reduce((sum, x) => sum + x.remaining, 0);
    const focusSubjects = leftAll > 0 ? focus : [{ name: 'Revision' }];
    const rowDate = new Date(today); rowDate.setDate(today.getDate() + d);

    schedule.push({
      dateLabel: rowDate.toLocaleDateString('en-US', { month:'short', day:'numeric' }),
      focusSubjects,
      topicTargets: leftAll > 0 ? topicTargets : ['Review notes', 'Practice questions'],
      hours: allocations.reduce((sum,a) => sum + a.hoursPerDay, 0),
    });

    if (leftAll <= 0) break;
  }

  const totalDays = daysUntil(examDate);
  const bufferDays = Math.max(0, totalDays - studyDays);
  for (let b = 0; b < bufferDays; b++) {
    const rowDate = new Date(today); rowDate.setDate(today.getDate() + studyDays + b);
    schedule.push({
      dateLabel: rowDate.toLocaleDateString('en-US', { month:'short', day:'numeric' }),
      focusSubjects: [{ name: 'Revision' }],
      topicTargets: ['Mock test', 'Review weak areas'],
      hours: allocations.reduce((sum,a) => sum + a.hoursPerDay, 0) * 0.7,
    });
  }

  return schedule;
}

function buildRecommendations(res) {
  const list = [];
  const mostAtRisk = res.priorities[0];
  const totalTopicsLeft = res.allocations.reduce((sum,a) => sum + a.remainingTopics, 0);

  list.push({ icon: 'bi-bullseye', text: `You have ${totalTopicsLeft} remaining topics across ${res.allocations.length} subjects.` });
  list.push({ icon: 'bi-calendar2-check', text: `Protect your ${res.bufferDays}-day buffer for revision. Treat it as non-negotiable.` });

  if (mostAtRisk && mostAtRisk.risk >= 1.05) {
    list.push({ icon: 'bi-exclamation-triangle-fill', text: `${mostAtRisk.name} needs more attention. Add 0.5–1.0 extra hours/day or reduce minutes/topic until it becomes “On track”.` });
  } else {
    list.push({ icon: 'bi-check2-circle', text: 'Your current pace looks achievable. Stay consistent and keep ticking topics off.' });
  }

  if (res.totalDays <= 7) {
    list.push({ icon: 'bi-lightning-charge-fill', text: 'Exam is soon. Prioritise high-weightage topics and practice questions over passive reading.' });
  }

  list.push({ icon: 'bi-stopwatch', text: 'Use the Pomodoro timer: 25 minutes focused work + 5 minutes break, repeated 4 times.' });
  list.push({ icon: 'bi-moon-stars-fill', text: 'Schedule the hardest subject earlier in your day when focus is highest.' });
  return list;
}

function normalizeAllocationHours(allocations, targetHours) {
  const clampMin = 0.25;

  // Prevent 0h allocations for subjects with remaining topics.
  allocations.forEach(a => {
    if (a.remainingTopics > 0 && a.hoursPerDay < clampMin) a.hoursPerDay = clampMin;
  });

  const step = 0.25;
  const maxIter = 200;
  let i = 0;

  const sumHours = () => allocations.reduce((s,a) => s + a.hoursPerDay, 0);
  let sum = sumHours();

  while (Math.abs(sum - targetHours) > 0.001 && i < maxIter) {
    i++;
    if (sum > targetHours) {
      allocations.sort((a,b) => (a.weight) - (b.weight));
      const pick = allocations.find(a => a.hoursPerDay - step >= clampMin);
      if (!pick) break;
      pick.hoursPerDay = roundToQuarter(pick.hoursPerDay - step);
    } else {
      allocations.sort((a,b) => (b.weight) - (a.weight));
      allocations[0].hoursPerDay = roundToQuarter(allocations[0].hoursPerDay + step);
    }
    sum = sumHours();
  }

  allocations.forEach(a => {
    a.dailyTopicsTarget = Math.max(0, Math.floor(((a.hoursPerDay * 60) / a.minsPerTopicAdj) + 0.0001));
    a.estDaysNeeded = a.dailyTopicsTarget > 0 ? Math.ceil(a.remainingTopics / a.dailyTopicsTarget) : Infinity;
  });
}

// ----------------------------
// Pomodoro Timer
// ----------------------------
let timerInterval = null;
let timerRunning = false;
let modeMins = 25;
let totalSecs = 25 * 60;
let remainingSecs = 25 * 60;
const RING_CIRC = 2 * Math.PI * 54; // r=54

function setPomodoroMode(mins, label, btn) {
  if (timerRunning) return;

  modeMins = mins;
  totalSecs = mins * 60;
  remainingSecs = totalSecs;

  document.querySelectorAll('.timer-tab').forEach(b => b.classList.remove('active'));
  btn?.classList.add('active');

  setText('timerLabel', label);
  updateTimerUI(1);
}

function toggleTimer() {
  timerRunning ? pauseTimer() : startTimer();
}

function startTimer() {
  timerRunning = true;
  const icon = document.getElementById('timerToggleIcon');
  if (icon) icon.className = 'bi bi-pause-fill';

  timerInterval = setInterval(() => {
    if (remainingSecs <= 0) {
      finishTimer();
      return;
    }
    remainingSecs--;
    updateTimerUI(remainingSecs / totalSecs);
  }, 1000);
}

function pauseTimer() {
  timerRunning = false;
  clearInterval(timerInterval);
  const icon = document.getElementById('timerToggleIcon');
  if (icon) icon.className = 'bi bi-play-fill';
}

function resetTimer() {
  pauseTimer();
  remainingSecs = totalSecs;
  updateTimerUI(1);
}

function skipTimer() {
  pauseTimer();
  remainingSecs = 0;
  finishTimer();
}

function finishTimer() {
  clearInterval(timerInterval);
  timerRunning = false;
  const icon = document.getElementById('timerToggleIcon');
  if (icon) icon.className = 'bi bi-play-fill';

  // Count only focus sessions (>= 20 minutes)
  if (modeMins >= 20) {
    state.timer.sessionsToday = (state.timer.sessionsToday || 0) + 1;
    state.timer.focusMinsTotal = (state.timer.focusMinsTotal || 0) + modeMins;
    bumpStreak();
    saveState();
    renderTimerStats();
    renderDashboard();
    toast('Focus session complete. Take a short break.', 'success');
  } else {
    toast('Break finished. Ready to focus again?', 'info');
  }

  remainingSecs = totalSecs;
  updateTimerUI(1);
}

function updateTimerUI(fraction) {
  const m = Math.floor(remainingSecs / 60).toString().padStart(2, '0');
  const s = (remainingSecs % 60).toString().padStart(2, '0');
  setText('timerDisplay', `${m}:${s}`);

  const ring = document.getElementById('timerRing');
  if (ring) {
    ring.style.strokeDasharray = String(RING_CIRC);
    ring.style.strokeDashoffset = String(RING_CIRC * (1 - fraction));
  }
}

function renderTimerStats() {
  setText('sessionsToday', String(state.timer.sessionsToday || 0));
  setText('streakCount', String(state.timer.streak || 0));
}

function bumpStreak() {
  const today = new Date().toDateString();
  if (state.timer.lastStreakDate === today) return;
  state.timer.streak = (state.timer.streak || 0) + 1;
  state.timer.lastStreakDate = today;
}

// ----------------------------
// Tasks
// ----------------------------
function addTask() {
  const input = document.getElementById('taskInput');
  const text = (input?.value || '').trim();
  if (!text) return toast('Please enter a task.', 'warning');

  state.tasks.unshift({ id: uid(), text, done: false, createdAt: Date.now() });
  saveState();
  if (input) input.value = '';
  renderTasks();
  renderDashboard();
}

function toggleTask(id) {
  const t = state.tasks.find(x => x.id === id);
  if (!t) return;
  t.done = !t.done;
  saveState();
  renderTasks();
  renderDashboard();
}

function deleteTask(id) {
  state.tasks = state.tasks.filter(t => t.id !== id);
  saveState();
  renderTasks();
  renderDashboard();
}

function renderTasks() {
  const list = document.getElementById('taskList');
  const empty = document.getElementById('tasksEmpty');
  const progressWrap = document.getElementById('taskProgressWrap');
  if (!list || !empty || !progressWrap) return;

  list.innerHTML = '';
  if (!state.tasks.length) {
    empty.style.display = '';
    progressWrap.style.display = 'none';
    return;
  }
  empty.style.display = 'none';

  const done = state.tasks.filter(t => t.done).length;
  const pct = Math.round((done / state.tasks.length) * 100);
  progressWrap.style.display = '';
  setText('taskProgressLabel', `${pct}%`);
  const bar = document.getElementById('taskProgressBar');
  if (bar) bar.style.width = `${pct}%`;

  state.tasks.forEach(t => {
    const li = document.createElement('li');
    li.className = 'task-item' + (t.done ? ' done' : '');
    li.innerHTML = `
      <div class="task-check ${t.done ? 'checked' : ''}" onclick="toggleTask('${t.id}')">${t.done ? '<i class="bi bi-check-lg"></i>' : ''}</div>
      <div class="task-text">${escapeHtml(t.text)}</div>
      <button class="task-delete" onclick="deleteTask('${t.id}')" title="Delete"><i class="bi bi-trash3"></i></button>
    `;
    list.appendChild(li);
  });
}

// ----------------------------
// Quotes
// ----------------------------
function renderQuotes() {
  refreshFeaturedQuote();
  const grid = document.getElementById('quotesGrid');
  if (!grid) return;

  const picks = [...QUOTES].sort(() => Math.random() - 0.5).slice(0, 8);
  grid.innerHTML = picks.map(q => `
    <div class="col-sm-6 col-lg-3">
      <div class="quote-mini">
        <p>“${escapeHtml(q.text)}”</p>
        <cite>— ${escapeHtml(q.author)}</cite>
      </div>
    </div>
  `).join('');
}

function refreshFeaturedQuote() {
  const q = randomQuote();
  setText('featuredQuoteText', `“${q.text}”`);
  setText('featuredQuoteAuthor', '— ' + q.author);
}

// ----------------------------
// Utilities
// ----------------------------
function uid() {
  return Math.random().toString(36).slice(2) + Date.now().toString(36);
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function formatDate(dateStr) {
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function daysUntil(dateStr) {
  const d = new Date(dateStr); d.setHours(0,0,0,0);
  const t = new Date(); t.setHours(0,0,0,0);
  return Math.floor((d - t) / 86400000);
}

function isFutureDate(dateStr) {
  return daysUntil(dateStr) > 0;
}

function roundToQuarter(n) {
  return Math.round(n * 4) / 4;
}

function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;');
}

function formatMins(mins) {
  if (mins < 60) return `${mins}m`;
  return `${(mins / 60).toFixed(1)}h`;
}

// ----------------------------
// Toast
// ----------------------------
function toast(message, type = 'info') {
  const wrap = document.getElementById('toastContainer');
  if (!wrap) return;

  const el = document.createElement('div');
  el.className = `toast-msg ${type}`;
  el.textContent = message;
  wrap.appendChild(el);

  setTimeout(() => {
    el.style.opacity = '0';
    el.style.transform = 'translateX(10px)';
  }, 2400);
  setTimeout(() => el.remove(), 2850);
}