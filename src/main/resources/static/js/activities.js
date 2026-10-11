document.addEventListener('DOMContentLoaded', () => {
    const activityModal = document.querySelector('[data-activity-modal]');
    const participantModal = document.querySelector('[data-participant-modal]');
    const activityForm = document.querySelector('#activity-form');
    const participantForm = document.querySelector('#participant-form');
    const attendanceForm = document.querySelector('#attendance-form');
    const activityFields = {
        title: document.querySelector('#activity-title'), type: document.querySelector('#activity-type'),
        activityDate: document.querySelector('#activity-date'), location: document.querySelector('#activity-location')
    };
    let currentActivityId = null;

    const toggle = (modal, open) => {
        if (!modal) return;
        modal.classList.toggle('is-open', open);
        modal.setAttribute('aria-hidden', String(!open));
    };

    const openActivity = (row) => {
        const editing = Boolean(row);
        document.querySelector('#activity-modal-title').textContent = editing ? '编辑活动' : '新增活动';
        document.querySelector('#activity-submit').textContent = editing ? '保存修改' : '保存活动';
        activityForm.action = editing ? `/activities/${row.dataset.id}` : '/activities';
        activityFields.title.value = editing ? row.dataset.title : '';
        activityFields.type.value = editing ? row.dataset.type : '主题党日';
        activityFields.activityDate.value = editing ? row.dataset.activityDate : '';
        activityFields.location.value = editing ? (row.dataset.location || '') : '';
        toggle(activityModal, true);
        activityFields.title.focus();
    };

    const openParticipants = async (row) => {
        currentActivityId = row.dataset.id;
        document.querySelector('#participant-modal-title').textContent = `${row.dataset.title} · 活动参与人`;
        document.querySelectorAll('#participant-form input[type="checkbox"]').forEach((input) => { input.checked = false; });
        document.querySelector('#recognition-result').hidden = true;
        document.querySelector('#attendance-status').textContent = '';
        toggle(participantModal, true);
        try {
            const response = await fetch(`/activities/${currentActivityId}/participants`);
            if (!response.ok) throw new Error('读取参与人失败');
            const participants = await response.json();
            const ids = new Set(participants.map((member) => String(member.id)));
            document.querySelectorAll('#participant-form input[type="checkbox"]').forEach((input) => { input.checked = ids.has(input.value); });
        } catch (error) {
            document.querySelector('#attendance-status').textContent = error.message;
        }
    };

    document.querySelector('[data-open-activity-modal]')?.addEventListener('click', () => openActivity());
    document.querySelectorAll('.edit-activity-button').forEach((button) => button.addEventListener('click', () => openActivity(button.closest('.activity-row'))));
    document.querySelectorAll('.participant-button').forEach((button) => button.addEventListener('click', () => openParticipants(button.closest('.activity-row'))));
    document.querySelectorAll('.attendance-button').forEach((button) => button.addEventListener('click', () => openParticipants(button.closest('.activity-row'))));
    document.querySelectorAll('[data-close-activity-modal]').forEach((button) => button.addEventListener('click', () => toggle(activityModal, false)));
    document.querySelectorAll('[data-close-participant-modal]').forEach((button) => button.addEventListener('click', () => toggle(participantModal, false)));
    [activityModal, participantModal].forEach((modal) => modal?.addEventListener('click', (event) => { if (event.target === modal) toggle(modal, false); }));
    document.addEventListener('keydown', (event) => { if (event.key === 'Escape') { toggle(activityModal, false); toggle(participantModal, false); } });

    participantForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const response = await fetch(`/activities/${currentActivityId}/participants`, { method: 'POST', body: new FormData(participantForm) });
        if (!response.ok) { document.querySelector('#attendance-status').textContent = '保存参与人失败，请重试。'; return; }
        const result = await response.json();
        updateCount(result.participantCount);
        document.querySelector('#attendance-status').textContent = '参与人已保存。';
    });

    attendanceForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const file = document.querySelector('#attendance-file');
        const status = document.querySelector('#attendance-status');
        if (!file.files.length) return;
        status.textContent = '正在识别签到表，请稍候……';
        const body = new FormData();
        body.append('file', file.files[0]);
        try {
            const response = await fetch(`/activities/${currentActivityId}/attendance`, { method: 'POST', body });
            const result = await response.json();
            if (!response.ok) throw new Error(result.message || '识别失败，请检查文件格式。');
            const matched = new Set(result.matchedNames);
            document.querySelectorAll('#participant-form input[type="checkbox"]').forEach((input) => { input.checked = matched.has(input.dataset.memberName); });
            updateCount(result.participantCount);
            document.querySelector('#recognition-result').hidden = false;
            document.querySelector('#matched-result').textContent = result.matchedNames.length ? `已匹配：${result.matchedNames.join('、')}` : '已匹配：无';
            const unmatched = document.querySelector('#unmatched-result');
            unmatched.textContent = result.unmatchedNames.length ? `未匹配：${result.unmatchedNames.join('、')}（请核对姓名）` : '未匹配：无';
            unmatched.classList.toggle('unmatched', result.unmatchedNames.length > 0);
            status.textContent = '识别完成，参与人已更新。';
        } catch (error) { status.textContent = error.message; }
    });

    function updateCount(count) {
        const element = document.querySelector(`[data-count-id="${currentActivityId}"]`);
        if (element) element.textContent = `${count} 人`;
    }
});
