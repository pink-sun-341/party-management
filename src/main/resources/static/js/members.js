document.addEventListener('DOMContentLoaded', () => {
    const modal = document.querySelector('[data-member-modal]');
    const form = document.querySelector('#member-form');
    const title = document.querySelector('#member-modal-title');
    const submit = document.querySelector('#member-submit');
    if (!modal || !form) return;

    const fields = {
        id: document.querySelector('#member-id'), name: document.querySelector('#member-name'),
        gender: document.querySelector('#member-gender'), phone: document.querySelector('#member-phone'),
        status: document.querySelector('#member-status'), applicationDate: document.querySelector('#member-application-date'),
        activistDate: document.querySelector('#member-activist-date'), developmentObjectDate: document.querySelector('#member-development-object-date'),
        probationaryDate: document.querySelector('#member-probationary-date'), fullMemberDate: document.querySelector('#member-full-member-date')
    };

    const openModal = (member) => {
        const editing = Boolean(member);
        title.textContent = editing ? '编辑党员档案' : '新增党员';
        submit.textContent = editing ? '保存修改' : '保存档案';
        form.action = editing ? `/members/${member.id}` : '/members';
        Object.entries(fields).forEach(([key, field]) => {
            if (field) field.value = editing ? (member[key] || '') : (key === 'status' ? '正式党员' : '');
        });
        modal.classList.add('is-open');
        modal.setAttribute('aria-hidden', 'false');
        fields.name.focus();
    };

    const closeModal = () => {
        modal.classList.remove('is-open');
        modal.setAttribute('aria-hidden', 'true');
    };

    document.querySelector('[data-open-member-modal]')?.addEventListener('click', () => openModal());
    document.querySelectorAll('[data-close-member-modal]').forEach((button) => button.addEventListener('click', closeModal));
    modal.addEventListener('click', (event) => { if (event.target === modal) closeModal(); });
    document.addEventListener('keydown', (event) => { if (event.key === 'Escape' && modal.classList.contains('is-open')) closeModal(); });

    document.querySelectorAll('.member-row').forEach((row) => {
        row.querySelector('.edit-member-button')?.addEventListener('click', () => openModal({
            id: row.dataset.id, name: row.dataset.name, gender: row.dataset.gender, phone: row.dataset.phone,
            status: row.dataset.status, applicationDate: row.dataset.applicationDate, activistDate: row.dataset.activistDate,
            developmentObjectDate: row.dataset.developmentObjectDate, probationaryDate: row.dataset.probationaryDate,
            fullMemberDate: row.dataset.fullMemberDate
        }));
    });
});
