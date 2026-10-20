document.addEventListener('DOMContentLoaded', () => {
    const activityModal = document.querySelector('[data-activity-modal]');
    const participantModal = document.querySelector('[data-participant-modal]');
    const detailModal = document.querySelector('[data-detail-modal]');
    const imageModal = document.querySelector('[data-image-modal]');
    const lightbox = document.querySelector('[data-image-lightbox]');
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

    const openDetails = async (row) => {
        currentActivityId = row.dataset.id;
        document.querySelector('#detail-modal-title').textContent = `${row.dataset.title} · 活动详情`;
        document.querySelector('#detail-date').textContent = row.dataset.activityDate || '未填写';
        document.querySelector('#detail-type').textContent = row.dataset.type || '未填写';
        document.querySelector('#detail-location').textContent = row.dataset.location || '未填写';
        document.querySelector('#detail-count').textContent = '正在加载';
        const list = document.querySelector('#detail-participant-list');
        const imageList = document.querySelector('#detail-image-list');
        list.innerHTML = '<p class="detail-loading">正在加载参与人……</p>';
        imageList.innerHTML = '<p class="detail-loading">正在加载活动图片……</p>';
        toggle(detailModal, true);
        try {
            const [participantResponse, imageResponse] = await Promise.all([
                fetch(`/activities/${currentActivityId}/participants`),
                fetch(`/activities/${currentActivityId}/images`)
            ]);
            if (!participantResponse.ok) throw new Error('读取参与人失败');
            if (!imageResponse.ok) throw new Error('读取活动图片失败');
            const participants = await participantResponse.json();
            const images = await imageResponse.json();
            document.querySelector('#detail-count').textContent = `${participants.length} 人`;
            if (!participants.length) {
                list.innerHTML = '<p class="detail-empty">暂未关联参与党员。</p>';
            } else {
                list.innerHTML = participants.map((member) => {
                    const name = escapeHtml(member.name || '未填写姓名');
                    const status = escapeHtml(member.status || '未填写状态');
                    const phone = member.phone ? ` · ${escapeHtml(member.phone)}` : '';
                    return `<div class="detail-participant"><span class="detail-avatar">${name.substring(0, 1)}</span><span><b>${name}</b><small>${status}${phone}</small></span></div>`;
                }).join('');
            }
            renderDetailImages(images);
        } catch (error) {
            document.querySelector('#detail-count').textContent = '读取失败';
            list.innerHTML = `<p class="detail-empty">${error.message}</p>`;
            imageList.innerHTML = '<p class="detail-empty">活动图片暂时无法读取。</p>';
        }
    };

    const openImageUpload = (row) => {
        currentActivityId = row.dataset.id;
        document.querySelector('#image-modal-title').textContent = `${row.dataset.title} · 活动图片`;
        document.querySelector('#activity-images').value = '';
        document.querySelector('#image-preview-list').innerHTML = '';
        document.querySelector('#image-upload-status').textContent = '';
        toggle(imageModal, true);
    };

    document.querySelector('[data-open-activity-modal]')?.addEventListener('click', () => openActivity());
    document.querySelectorAll('.edit-activity-button').forEach((button) => button.addEventListener('click', () => openActivity(button.closest('.activity-row'))));
    document.querySelectorAll('.participant-button').forEach((button) => button.addEventListener('click', () => openParticipants(button.closest('.activity-row'))));
    document.querySelectorAll('.detail-button').forEach((button) => button.addEventListener('click', () => openDetails(button.closest('.activity-row'))));
    document.querySelectorAll('.image-button').forEach((button) => button.addEventListener('click', () => openImageUpload(button.closest('.activity-row'))));
    document.querySelectorAll('[data-close-activity-modal]').forEach((button) => button.addEventListener('click', () => toggle(activityModal, false)));
    document.querySelectorAll('[data-close-participant-modal]').forEach((button) => button.addEventListener('click', () => toggle(participantModal, false)));
    document.querySelectorAll('[data-close-detail-modal]').forEach((button) => button.addEventListener('click', () => toggle(detailModal, false)));
    document.querySelectorAll('[data-close-image-modal]').forEach((button) => button.addEventListener('click', () => toggle(imageModal, false)));
    document.querySelector('[data-close-lightbox]')?.addEventListener('click', () => toggle(lightbox, false));
    lightbox?.addEventListener('click', (event) => { if (event.target === lightbox) toggle(lightbox, false); });
    [activityModal, participantModal, detailModal, imageModal].forEach((modal) => modal?.addEventListener('click', (event) => { if (event.target === modal) toggle(modal, false); }));
    document.addEventListener('keydown', (event) => { if (event.key === 'Escape') { toggle(activityModal, false); toggle(participantModal, false); toggle(detailModal, false); toggle(imageModal, false); toggle(lightbox, false); } });

    document.querySelector('#activity-images')?.addEventListener('change', (event) => {
        const files = Array.from(event.target.files || []);
        const preview = document.querySelector('#image-preview-list');
        const status = document.querySelector('#image-upload-status');
        preview.innerHTML = '';
        if (files.length > 8) {
            status.textContent = '一次最多选择 8 张图片。';
            event.target.value = '';
            return;
        }
        status.textContent = files.length ? `已选择 ${files.length} 张图片，确认预览无误后上传。` : '';
        files.forEach((file) => {
            if (!file.type.startsWith('image/')) return;
            const tile = document.createElement('div');
            tile.className = 'image-preview-tile';
            const image = document.createElement('img');
            image.alt = file.name;
            image.src = URL.createObjectURL(file);
            const name = document.createElement('small');
            name.textContent = file.name;
            tile.append(image, name);
            preview.append(tile);
        });
    });

    document.querySelector('#upload-images-submit')?.addEventListener('click', async () => {
        const input = document.querySelector('#activity-images');
        const status = document.querySelector('#image-upload-status');
        if (!input.files.length) { status.textContent = '请先选择活动图片。'; return; }
        const body = new FormData();
        Array.from(input.files).forEach((file) => body.append('files', file));
        status.textContent = '正在上传图片，请稍候……';
        try {
            const response = await fetch(`/activities/${currentActivityId}/images`, { method: 'POST', body });
            const result = await response.json();
            if (!response.ok) throw new Error(result.message || '图片上传失败');
            status.textContent = `上传完成，共保存 ${result.length} 张活动图片。`;
            document.querySelector('#image-preview-list').innerHTML = '';
            input.value = '';
        } catch (error) { status.textContent = error.message; }
    });

    function renderDetailImages(images) {
        const list = document.querySelector('#detail-image-list');
        document.querySelector('#detail-image-count').textContent = `${images.length} 张`;
        if (!images.length) { list.innerHTML = '<p class="detail-empty">暂未上传活动图片。</p>'; return; }
        list.innerHTML = '';
        images.forEach((item) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'detail-image-tile';
            button.title = `查看 ${item.originalName || '活动图片'}`;
            const image = document.createElement('img');
            image.src = item.url;
            image.alt = item.originalName || '活动图片';
            button.append(image);
            button.addEventListener('click', () => {
                document.querySelector('#lightbox-image').src = item.url;
                document.querySelector('#lightbox-image').alt = item.originalName || '活动图片';
                toggle(lightbox, true);
            });
            list.append(button);
        });
    }

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

    function escapeHtml(value) {
        return String(value).replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[character]));
    }
});
