(() => {
    const $ = s => document.querySelector(s);
    const $$ = s => document.querySelectorAll(s);
    const app = $('#app');

    const CATS = [
        { id: 'food', name: 'Food', emoji: '🍔', color: '#FF9800' },
        { id: 'transport', name: 'Transport', emoji: '🚌', color: '#2196F3' },
        { id: 'shopping', name: 'Shopping', emoji: '🛍', color: '#9C27B0' },
        { id: 'bills', name: 'Bills', emoji: '📄', color: '#F44336' },
        { id: 'entertainment', name: 'Entertainment', emoji: '🎬', color: '#E91E63' },
        { id: 'health', name: 'Health', emoji: '💊', color: '#4CAF50' },
        { id: 'grocery', name: 'Grocery', emoji: '🛒', color: '#009688' },
        { id: 'salary', name: 'Salary', emoji: '💰', color: '#2E7D32' },
        { id: 'other', name: 'Other', emoji: '📦', color: '#607D8B' },
    ];

    const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
    const DOW = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];

    let expenses = JSON.parse(localStorage.getItem('pocket_expenses') || '[]');
    let profileName = localStorage.getItem('pocket_name') || 'User';
    let theme = localStorage.getItem('pocket_theme') || 'system';
    let currentScreen = 'splash';
    let calYear, calMonth, selectedDate;
    let filterType = 'monthly';
    let filterStart = null, filterEnd = null;

    function save() { localStorage.setItem('pocket_expenses', JSON.stringify(expenses)); }
    function saveName() { localStorage.setItem('pocket_name', profileName); }
    function applyTheme() {
        const isDark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
        document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
    }
    applyTheme();
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => { if (theme === 'system') applyTheme(); });

    function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 8); }
    function fmt(n) { return n.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 }); }
    function fmtDate(d) { const dt = new Date(d); return `${dt.getDate()} ${MONTHS[dt.getMonth()].slice(0,3)} ${dt.getFullYear()}`; }
    function sameDay(a, b) { return a.getFullYear()===b.getFullYear() && a.getMonth()===b.getMonth() && a.getDate()===b.getDate(); }

    function snackbar(msg) {
        const el = document.createElement('div');
        el.className = 'snackbar';
        el.textContent = msg;
        document.body.appendChild(el);
        setTimeout(() => el.remove(), 2000);
    }

    function filteredExpenses() {
        let list = expenses;
        if (filterType === 'daily' && selectedDate) {
            list = list.filter(e => sameDay(new Date(e.date), selectedDate));
        } else if (filterType === 'custom' && filterStart && filterEnd) {
            const s = new Date(filterStart), e = new Date(filterEnd);
            s.setHours(0,0,0,0); e.setHours(23,59,59,999);
            list = list.filter(ev => { const d = new Date(ev.date); return d >= s && d <= e; });
        } else if (filterType === 'monthly' || filterType === 'daily') {
            const now = new Date();
            list = list.filter(e => { const d = new Date(e.date); return d.getMonth()===now.getMonth() && d.getFullYear()===now.getFullYear(); });
        }
        return list;
    }

    function totalIncome(list) { return list.filter(e => e.type === 'income').reduce((s, e) => s + e.amount, 0); }
    function totalExpense(list) { return list.filter(e => e.type === 'expense').reduce((s, e) => s + e.amount, 0); }

    function getCat(id) { return CATS.find(c => c.id === id) || CATS[CATS.length - 1]; }

    // ─── NAV ─────────────────────────────────────────────
    function nav(screen) {
        currentScreen = screen;
        render();
    }

    // ─── RENDER ──────────────────────────────────────────
    function render() {
        applyTheme();
        if (currentScreen === 'splash') { renderSplash(); return; }
        const screens = {
            home: renderHome,
            calendar: renderCalendar,
            add: renderAdd,
            insights: renderInsights,
            settings: renderSettings,
        };
        let html = `<div class="screen active" id="screen-${currentScreen}"></div>`;
        app.innerHTML = html;
        (screens[currentScreen] || renderHome)();
        if (currentScreen !== 'add') renderNav();
    }

    function renderNav() {
        const fab = currentScreen === 'home' || currentScreen === 'calendar' ? `<button class="fab" onclick="window._nav('add')">+</button>` : '';
        app.innerHTML += `
            ${fab}
            <nav class="bottom-nav">
                <div class="nav-item ${currentScreen==='home'?'active':''}" onclick="window._nav('home')">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg>
                    Home
                </div>
                <div class="nav-item ${currentScreen==='calendar'?'active':''}" onclick="window._nav('calendar')">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14a2 2 0 002 2h14a2 2 0 002-2V5a2 2 0 00-2-2zm0 16H5V8h14v11z"/></svg>
                    Calendar
                </div>
                <div class="nav-item ${currentScreen==='insights'?'active':''}" onclick="window._nav('insights')">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/></svg>
                    Insights
                </div>
                <div class="nav-item ${currentScreen==='settings'?'active':''}" onclick="window._nav('settings')">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M19.14 12.94a7.014 7.014 0 000-1.88l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.49.49 0 00-.59-.22l-2.39.96a7.04 7.04 0 00-1.63-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.63.94l-2.39-.96a.49.49 0 00-.59.22L2.74 8.87a.48.48 0 00.12.61l2.03 1.58a7.014 7.014 0 000 1.88l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.04.7 1.63.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.63-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 00-.12-.61l-2.03-1.58zM12 15.6A3.6 3.6 0 1115.6 12 3.6 3.6 0 0112 15.6z"/></svg>
                    Settings
                </div>
            </nav>`;
    }

    // ─── SPLASH ──────────────────────────────────────────
    function renderSplash() {
        app.innerHTML = `
            <div id="splash">
                <h1>Pocket</h1>
                <p>Personal Expense Manager</p>
            </div>`;
        setTimeout(() => nav('home'), 1500);
    }

    // ─── HOME ────────────────────────────────────────────
    function renderHome() {
        const now = new Date();
        const monthExpenses = expenses.filter(e => {
            const d = new Date(e.date);
            return d.getMonth()===now.getMonth() && d.getFullYear()===now.getFullYear();
        });
        const inc = totalIncome(monthExpenses);
        const exp = totalExpense(monthExpenses);
        const recent = [...expenses].sort((a,b) => new Date(b.date)-new Date(a.date)).slice(0, 10);

        $('#screen-home').innerHTML = `
            <div class="greeting">Good ${now.getHours()<12?'Morning':now.getHours()<17?'Afternoon':'Evening'},</div>
            <div class="name">${profileName}</div>
            <div class="card">
                <div class="label">This Month's Balance</div>
                <div class="amount">₹${fmt(inc - exp)}</div>
                <div class="sub">₹${fmt(exp)} spent · ₹${fmt(inc)} earned</div>
                <div class="card-row">
                    <div class="col"><div class="val" style="color:#fff">₹${fmt(inc)}</div><div class="lbl">Income</div></div>
                    <div class="col"><div class="val" style="color:#fff">₹${fmt(exp)}</div><div class="lbl">Expense</div></div>
                </div>
            </div>
            <div class="details-toggle">
                <span class="section-title">Recent Transactions</span>
            </div>
            <div id="recent-list">
                ${recent.length ? recent.map(e => txnHTML(e)).join('') : '<div class="empty-msg">No transactions yet. Tap + to add one!</div>'}
            </div>`;
    }

    function txnHTML(e) {
        const cat = getCat(e.category);
        return `<div class="txn-item" onclick="window._details('${e.id}')">
            <div class="txn-icon">${cat.emoji}</div>
            <div class="txn-info">
                <div class="cat">${cat.name}</div>
                <div class="merchant">${e.merchant || '—'}</div>
            </div>
            <div class="txn-amount" style="color:${e.type==='expense'?'var(--error)':'#2E7D32'}">
                ${e.type==='expense'?'-':'+'}₹${fmt(e.amount)}
            </div>
        </div>`;
    }

    // ─── CALENDAR ────────────────────────────────────────
    function renderCalendar() {
        const now = new Date();
        if (!calYear) { calYear = now.getFullYear(); calMonth = now.getMonth(); }
        if (!selectedDate) selectedDate = now;

        const first = new Date(calYear, calMonth, 1);
        const startDay = first.getDay();
        const daysInMonth = new Date(calYear, calMonth+1, 0).getDate();
        const daysInPrev = new Date(calYear, calMonth, 0).getDate();

        const dayExpenses = {};
        expenses.forEach(e => {
            const d = new Date(e.date);
            if (d.getMonth()===calMonth && d.getFullYear()===calYear) {
                const key = d.getDate();
                if (!dayExpenses[key]) dayExpenses[key] = 0;
                dayExpenses[key] += e.amount;
            }
        });

        let cells = DOW.map(d => `<div class="cal-dow">${d}</div>`).join('');
        for (let i = 0; i < startDay; i++) {
            cells += `<div class="cal-day other-month">${daysInPrev - startDay + i + 1}</div>`;
        }
        for (let d = 1; d <= daysInMonth; d++) {
            const date = new Date(calYear, calMonth, d);
            const isToday = sameDay(date, now);
            const isSelected = sameDay(date, selectedDate);
            const hasTxn = dayExpenses[d];
            cells += `<div class="cal-day${isToday?' today':''}${isSelected?' selected':''}" onclick="window._calSelect(${d})">
                ${d}${hasTxn ? '<div class="cal-dot"></div>' : ''}
            </div>`;
        }
        const totalCells = startDay + daysInMonth;
        const remaining = (7 - (totalCells % 7)) % 7;
        for (let i = 1; i <= remaining; i++) {
            cells += `<div class="cal-day other-month">${i}</div>`;
        }

        const dayTxns = expenses.filter(e => sameDay(new Date(e.date), selectedDate));
        const dayInc = totalIncome(dayTxns);
        const dayExp = totalExpense(dayTxns);

        $('#screen-calendar').innerHTML = `
            <div class="cal-header">
                <button onclick="window._calPrev()">‹</button>
                <h2>${MONTHS[calMonth]} ${calYear}</h2>
                <button onclick="window._calNext()">›</button>
            </div>
            <div class="cal-grid">${cells}</div>
            <div style="margin-top:16px">
                <div class="section-title">${selectedDate.getDate()} ${MONTHS[selectedDate.getMonth()]} — Day Summary</div>
                <div class="card" style="margin-top:8px">
                    <div class="sub">Income: ₹${fmt(dayInc)} · Expense: ₹${fmt(dayExp)} · Balance: ₹${fmt(dayInc-dayExp)}</div>
                </div>
                <div style="margin-top:12px">
                    ${dayTxns.length ? dayTxns.map(e => txnHTML(e)).join('') : '<div class="empty-msg">No transactions on this day</div>'}
                </div>
            </div>`;
    }

    // ─── ADD EXPENSE ─────────────────────────────────────
    function renderAdd() {
        const today = new Date().toISOString().split('T')[0];
        $('#screen-add').innerHTML = `
            <div style="display:flex;align-items:center;margin-bottom:20px">
                <button onclick="window._nav('home')" style="background:none;border:none;font-size:1.5rem;cursor:pointer;color:var(--on-bg)">←</button>
                <h2 style="flex:1;text-align:center">Add Transaction</h2>
            </div>
            <div class="type-toggle">
                <div class="type-btn active" id="type-expense" onclick="window._setType('expense')">Expense</div>
                <div class="type-btn income" id="type-income" onclick="window._setType('income')">Income</div>
            </div>
            <div class="form-group">
                <label class="form-label">Amount</label>
                <div class="prefix">
                    <input type="number" class="form-input amount" id="add-amount" placeholder="0" min="0" step="1">
                </div>
            </div>
            <div class="form-group">
                <label class="form-label">Category</label>
                <div class="cat-grid" id="cat-grid">
                    ${CATS.map(c => `<div class="cat-item${c.id==='food'?' selected':''}" data-cat="${c.id}" onclick="window._selectCat('${c.id}')">
                        <div class="cat-icon">${c.emoji}</div>
                        ${c.name}
                    </div>`).join('')}
                </div>
            </div>
            <div class="form-group">
                <label class="form-label">Merchant / Note (optional)</label>
                <input type="text" class="form-input" id="add-merchant" placeholder="e.g. Swiggy, Rent, Salary...">
            </div>
            <div class="form-group">
                <label class="form-label">Date</label>
                <input type="date" class="form-input" id="add-date" value="${today}">
            </div>
            <button class="btn" onclick="window._saveTxn()">Save Transaction</button>`;
    }

    let addType = 'expense', addCat = 'food';

    // ─── INSIGHTS ────────────────────────────────────────
    function renderInsights() {
        const now = new Date();
        const monthExpenses = expenses.filter(e => {
            const d = new Date(e.date);
            return d.getMonth()===now.getMonth() && d.getFullYear()===now.getFullYear() && e.type==='expense';
        });

        const catTotals = {};
        monthExpenses.forEach(e => {
            catTotals[e.category] = (catTotals[e.category] || 0) + e.amount;
        });
        const maxCat = Math.max(...Object.values(catTotals), 1);

        const barData = [];
        for (let i = 5; i >= 0; i--) {
            const m = new Date(now.getFullYear(), now.getMonth() - i, 1);
            const me = expenses.filter(e => {
                const d = new Date(e.date);
                return d.getMonth()===m.getMonth() && d.getFullYear()===m.getFullYear() && e.type==='expense';
            });
            barData.push({ label: MONTHS[m.getMonth()].slice(0,3), total: totalExpense(me) });
        }
        const maxBar = Math.max(...barData.map(b => b.total), 1);

        const dailyData = [];
        for (let i = 29; i >= 0; i--) {
            const d = new Date(now); d.setDate(d.getDate()-i);
            const de = expenses.filter(e => sameDay(new Date(e.date), d) && e.type==='expense');
            dailyData.push({ label: d.getDate().toString(), total: totalExpense(de) });
        }
        const maxDaily = Math.max(...dailyData.map(d => d.total), 1);

        $('#screen-insights').innerHTML = `
            <h2 style="margin-bottom:12px">Insights</h2>
            <div class="tabs">
                <div class="tab active" onclick="window._insightTab(this,'daily')">Daily</div>
                <div class="tab" onclick="window._insightTab(this,'monthly')">Monthly</div>
            </div>
            <div id="chart-daily" style="display:block">
                <div class="insight-card">
                    <div class="section-title">Last 30 Days</div>
                    <div class="bar-chart">
                        ${dailyData.map(d => `<div class="bar-col">
                            <div class="bar" style="height:${Math.max((d.total/maxDaily)*180,4)}px"></div>
                            <div class="bar-label">${d.label}</div>
                        </div>`).join('')}
                    </div>
                </div>
            </div>
            <div id="chart-monthly" style="display:none">
                <div class="insight-card">
                    <div class="section-title">Last 6 Months</div>
                    <div class="bar-chart">
                        ${barData.map(b => `<div class="bar-col">
                            <div class="bar" style="height:${Math.max((b.total/maxBar)*180,4)}px"></div>
                            <div class="bar-label">${b.label}</div>
                        </div>`).join('')}
                    </div>
                </div>
            </div>
            <div class="insight-card" style="margin-top:12px">
                <div class="section-title">Category Breakdown</div>
                ${Object.entries(catTotals).sort((a,b) => b[1]-a[1]).map(([cat, val]) => {
                    const c = getCat(cat);
                    return `<div class="cat-bar-row">
                        <div class="cat-bar-label">${c.emoji} ${c.name}</div>
                        <div class="cat-bar-track"><div class="cat-bar-fill" style="width:${(val/maxCat)*100}%;background:${c.color}"></div></div>
                        <div class="cat-bar-val">₹${fmt(val)}</div>
                    </div>`;
                }).join('') || '<div class="empty-msg">No expenses this month</div>'}
            </div>`;
    }

    // ─── SETTINGS ────────────────────────────────────────
    function renderSettings() {
        const expensesCount = expenses.length;
        const csvSize = new Blob([toCSV()]).size;

        $('#screen-settings').innerHTML = `
            <h2 style="margin-bottom:16px">Settings</h2>
            <div class="settings-section">
                <h3>Personal</h3>
                <div class="settings-row" onclick="window._editName()">
                    <div><div class="title">Your Name</div><div class="sub">${profileName}</div></div>
                    <span style="color:var(--outline)">›</span>
                </div>
            </div>
            <div class="settings-section">
                <h3>Appearance</h3>
                <div class="settings-row" onclick="window._pickTheme()">
                    <div><div class="title">Theme</div><div class="sub">${theme.charAt(0).toUpperCase()+theme.slice(1)}</div></div>
                    <span style="color:var(--outline)">›</span>
                </div>
            </div>
            <div class="settings-section">
                <h3>Data</h3>
                <div class="settings-row" onclick="window._exportJSON()">
                    <div><div class="title">Backup (JSON)</div><div class="sub">${expensesCount} transactions</div></div>
                    <span style="color:var(--outline)">↓</span>
                </div>
                <div class="settings-row" onclick="window._importJSON()">
                    <div><div class="title">Restore (JSON)</div><div class="sub">Import from file</div></div>
                    <span style="color:var(--outline)">↑</span>
                </div>
                <div class="settings-row" onclick="window._exportCSV()">
                    <div><div class="title">Export CSV</div><div class="sub">${(csvSize/1024).toFixed(1)} KB</div></div>
                    <span style="color:var(--outline)">↓</span>
                </div>
                <div class="settings-row" onclick="window._clearAll()">
                    <div><div class="title" style="color:var(--error)">Clear All Data</div></div>
                    <span style="color:var(--error)">🗑</span>
                </div>
            </div>
            <div class="settings-section">
                <h3>About</h3>
                <div class="settings-row">
                    <div><div class="title">Version</div><div class="sub">Pocket Web 1.0</div></div>
                </div>
                <div class="settings-row">
                    <div><div class="title">Developer</div><div class="sub">Sanjai · marxen.in</div></div>
                </div>
            </div>
            <input type="file" id="import-input" accept=".json" style="display:none" onchange="window._handleImport(event)">`;
    }

    // ─── CSV ─────────────────────────────────────────────
    function toCSV() {
        const header = 'Date,Type,Category,Amount,Merchant\n';
        const rows = expenses.map(e => {
            const d = new Date(e.date).toISOString().split('T')[0];
            return `${d},${e.type},${getCat(e.category).name},${e.amount},"${(e.merchant||'').replace(/"/g,'""')}"`;
        }).join('\n');
        return header + rows;
    }

    // ─── GLOBAL HANDLERS ─────────────────────────────────
    window._nav = nav;
    window._calPrev = () => { calMonth--; if (calMonth<0) { calMonth=11; calYear--; } render(); };
    window._calNext = () => { calMonth++; if (calMonth>11) { calMonth=0; calYear++; } render(); };
    window._calSelect = (d) => { selectedDate = new Date(calYear, calMonth, d); render(); };
    window._setType = (t) => {
        addType = t;
        $$('.type-btn').forEach(b => b.classList.remove('active'));
        $(`#type-${t}`).classList.add('active');
        $$('.cat-item').forEach(el => el.classList.remove('selected'));
        addCat = CATS[0].id;
        $(`.cat-item[data-cat="${addCat}"]`).classList.add('selected');
    };
    window._selectCat = (id) => {
        addCat = id;
        $$('.cat-item').forEach(el => el.classList.remove('selected'));
        $(`.cat-item[data-cat="${id}"]`).classList.add('selected');
    };
    window._saveTxn = () => {
        const amount = parseFloat($('#add-amount').value);
        if (!amount || amount <= 0) { snackbar('Enter a valid amount'); return; }
        expenses.push({
            id: uid(),
            type: addType,
            category: addCat,
            amount,
            merchant: $('#add-merchant').value.trim(),
            date: new Date($('#add-date').value).toISOString(),
            createdAt: new Date().toISOString(),
        });
        save();
        snackbar('Transaction saved!');
        nav('home');
    };
    window._details = (id) => {
        const e = expenses.find(x => x.id === id);
        if (!e) return;
        const cat = getCat(e.category);
        const overlay = document.createElement('div');
        overlay.className = 'dialog-overlay';
        overlay.innerHTML = `
            <div class="dialog">
                <div style="text-align:center;margin-bottom:16px">
                    <div style="width:48px;height:48px;border-radius:50%;background:${cat.color}20;display:flex;align-items:center;justify-content:center;margin:0 auto;font-size:1.5rem">${cat.emoji}</div>
                    <h3 style="margin-top:8px">${cat.name}</h3>
                    <div style="font-size:1.8rem;font-weight:700;color:${e.type==='expense'?'var(--error)':'#2E7D32'}">${e.type==='expense'?'-':'+'}₹${fmt(e.amount)}</div>
                    <div style="color:var(--outline);font-size:0.85rem">${e.merchant || 'No note'} · ${fmtDate(e.date)}</div>
                </div>
                <div class="dialog-actions" style="justify-content:center">
                    <button onclick="this.closest('.dialog-overlay').remove()" style="color:var(--outline)">Close</button>
                    <button onclick="window._deleteTxn('${e.id}');this.closest('.dialog-overlay').remove()" style="color:var(--error)">Delete</button>
                </div>
            </div>`;
        document.body.appendChild(overlay);
        overlay.addEventListener('click', ev => { if (ev.target === overlay) overlay.remove(); });
    };
    window._deleteTxn = (id) => {
        if (confirm('Delete this transaction?')) {
            expenses = expenses.filter(e => e.id !== id);
            save();
            snackbar('Deleted');
            render();
        }
    };
    window._insightTab = (el, type) => {
        $$('.tab').forEach(t => t.classList.remove('active'));
        el.classList.add('active');
        $('#chart-daily').style.display = type === 'daily' ? 'block' : 'none';
        $('#chart-monthly').style.display = type === 'monthly' ? 'block' : 'none';
    };
    window._editName = () => {
        const name = prompt('Enter your name:', profileName);
        if (name && name.trim()) {
            profileName = name.trim();
            saveName();
            render();
        }
    };
    window._pickTheme = () => {
        const t = prompt('Theme (light / dark / system):', theme);
        if (t && ['light','dark','system'].includes(t.trim())) {
            theme = t.trim();
            localStorage.setItem('pocket_theme', theme);
            render();
        }
    };
    window._exportJSON = () => {
        const blob = new Blob([JSON.stringify(expenses, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `pocket-backup-${new Date().toISOString().split('T')[0]}.json`;
        a.click(); URL.revokeObjectURL(url);
        snackbar('Backup downloaded');
    };
    window._importJSON = () => { $('#import-input').click(); };
    window._handleImport = (ev) => {
        const file = ev.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const data = JSON.parse(e.target.result);
                if (!Array.isArray(data)) throw new Error();
                if (confirm(`Import ${data.length} transactions? This will merge with existing data.`)) {
                    expenses = [...expenses, ...data];
                    save();
                    snackbar(`Imported ${data.length} transactions`);
                    render();
                }
            } catch { snackbar('Invalid file'); }
        };
        reader.readAsText(file);
        ev.target.value = '';
    };
    window._exportCSV = () => {
        const blob = new Blob([toCSV()], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `pocket-export-${new Date().toISOString().split('T')[0]}.csv`;
        a.click(); URL.revokeObjectURL(url);
        snackbar('CSV downloaded');
    };
    window._clearAll = () => {
        if (confirm('Delete ALL transactions? This cannot be undone.')) {
            expenses = [];
            save();
            snackbar('All data cleared');
            render();
        }
    };

    nav('splash');
})();
