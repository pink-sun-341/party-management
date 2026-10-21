document.addEventListener('DOMContentLoaded', () => {
    const activityModal = document.querySelector('[data-activity-modal]');
    const participantModal = document.querySelector('[data-participant-modal]');
    const detailModal = document.querySelector('[data-detail-modal]');
    const imageModal = document.querySelector('[data-image-modal]');
    const lightbox = document.querySelector('[data-image-lightbox]');
    const activityForm = document.querySelector('#activity-form');
    const participantForm = document.querySelector('#participant-form');
    const activityFields = {
        title: document.querySelector('#activity-title'),
        type: document.querySelector('#activity-type'),
        activityDate: document.querySelector('#activity-date'),
        location: document.querySelector('#activity-location')
    };
    const editorParticipantInputs = () => Array.from(document.querySelectorAll('#editor-participant-list input[type="checkbox"]'));
    const participantInputs = () => Array.from(document.querySelectorAll('#participant-form input[type="checkbox"]'));
    const memberSearchInputs = () => Array.from(document.querySelectorAll('[data-member-search]'));
    const editorImageInput = document.querySelector('#activity-editor-images');
    const uploadImageInput = document.querySelector('#activity-images');
    const editorPreview = document.querySelector('#editor-image-preview-list');
    const uploadPreview = document.querySelector('#image-preview-list');
    const editorStatus = document.querySelector('#activity-save-status');
    const uploadStatus = document.querySelector('#image-upload-status');
    const editorPreviewUrls = new Set();
    const uploadPreviewUrls = new Set();
    const allowedImageTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']);
    let currentActivityId = null;
    let editingActivityId = null;

    const toggle = (modal, open) => {
        if (!modal) return;
        modal.classList.toggle('is-open', open);
        modal.setAttribute('aria-hidden', String(!open));
    };

    const clearPreview = (container, urls) => {
        urls.forEach((url) => URL.revokeObjectURL(url));
        urls.clear();
        if (container) container.innerHTML = '';
    };

    const resetInput = (input) => {
        if (input) input.value = '';
    };

    const resetEditor = () => {
        editingActivityId = null;
        activityForm?.reset();
        if (activityFields.type) activityFields.type.value = '主题党日';
        editorParticipantInputs().forEach((input) => { input.checked = false; });
        resetMemberFilters();
        resetInput(editorImageInput);
        clearPreview(editorPreview, editorPreviewUrls);
        if (editorStatus) editorStatus.textContent = '';
    };

    const validateImages = (files, maximum) => {
        if (files.length > maximum) return `一次最多选择 ${maximum} 张图片。`;
        for (const file of files) {
            const type = (file.type || '').toLowerCase();
            const extension = (file.name || '').toLowerCase().split('.').pop();
            const allowedByName = new Set(['jpg', 'jpeg', 'png', 'webp', 'heic', 'heif']).has(extension);
            if (!allowedImageTypes.has(type) && type !== 'image/jpg' && !allowedByName) {
                return `${file.name} 不是支持的图片格式。`;
            }
            if (file.size > 10 * 1024 * 1024) {
                return `${file.name} 超过 10MB，无法上传。`;
            }
        }
        return '';
    };

    const renderLocalPreviews = (files, container, urls) => {
        clearPreview(container, urls);
        files.forEach((file) => {
            const tile = document.createElement('div');
            tile.className = 'image-preview-tile';
            const image = document.createElement('img');
            const url = URL.createObjectURL(file);
            urls.add(url);
            image.src = url;
            image.alt = file.name;
            const name = document.createElement('small');
            name.textContent = file.name;
            tile.append(image, name);
            container?.append(tile);
        });
    };

    const responseJson = async (response) => {
        const text = await response.text();
        let body = {};
        if (text) {
            try { body = JSON.parse(text); } catch (ignored) { body = {}; }
        }
        if (!response.ok) {
            if (response.status === 413) throw new Error('图片文件过大，请将单张图片控制在 10MB 以内。');
            throw new Error(body.message || '请求失败，请重试。');
        }
        return body;
    };

    const fetchJson = async (url, options = {}, timeout = 10000) => {
        const controller = new AbortController();
        const timer = window.setTimeout(() => controller.abort(), timeout);
        try {
            return await responseJson(await fetch(url, { ...options, signal: controller.signal }));
        } catch (error) {
            if (error.name === 'AbortError') throw new Error('请求超时，请确认服务已启动后重试。');
            throw error;
        } finally {
            window.clearTimeout(timer);
        }
    };

    const applyMemberFilter = (searchInput) => {
        const scope = searchInput.dataset.memberSearch === 'editor'
            ? '#editor-participant-list'
            : '#participant-form .member-check-list';
        const list = document.querySelector(scope);
        if (!list) return;
        const keyword = searchInput.value.trim().toLocaleLowerCase();
        const rows = Array.from(list.querySelectorAll('.member-check'));
        let visibleCount = 0;
        rows.forEach((row) => {
            const name = row.querySelector('input[data-member-name]')?.dataset.memberName || row.textContent;
            const visible = !keyword || name.toLocaleLowerCase().includes(keyword);
            row.hidden = !visible;
            if (visible) visibleCount += 1;
        });
        const count = document.querySelector(`[data-member-search-count="${searchInput.dataset.memberSearch}"]`);
        if (count) count.textContent = keyword ? `显示 ${visibleCount} / ${rows.length} 名` : `共 ${rows.length} 名`;
        const empty = list.querySelector('.member-filter-empty');
        if (empty) empty.hidden = rows.length === 0 || visibleCount > 0;
    };

    const resetMemberFilters = () => {
        memberSearchInputs().forEach((input) => {
            input.value = '';
            applyMemberFilter(input);
        });
    };

    const getCheckedIds = (inputs) => inputs.filter((input) => input.checked).map((input) => input.value);

    const replaceParticipants = async (activityId, inputs) => {
        const body = new FormData();
        getCheckedIds(inputs).forEach((id) => body.append('memberIds', id));
        return fetchJson(`/activities/${activityId}/participants`, { method: 'POST', body });
    };

    const uploadImages = async (activityId, files) => {
        if (!files.length) return [];
        const body = new FormData();
        files.forEach((file) => body.append('files', file));
        return fetchJson(`/activities/${activityId}/images`, { method: 'POST', body });
    };

    const openActivity = async (row) => {
        const editing = Boolean(row);
        resetEditor();
        editingActivityId = editing ? row.dataset.id : null;
        document.querySelector('#activity-modal-title').textContent = editing ? '编辑活动' : '新增活动';
        document.querySelector('#activity-submit').textContent = editing ? '保存修改' : '保存活动';
        activityFields.title.value = editing ? row.dataset.title : '';
        activityFields.type.value = editing ? row.dataset.type : '主题党日';
        activityFields.activityDate.value = editing ? row.dataset.activityDate : '';
        activityFields.location.value = editing ? (row.dataset.location || '') : '';
        toggle(activityModal, true);
        activityFields.title.focus();
        if (!editing) return;

        if (editorStatus) editorStatus.textContent = '正在加载已关联的参与人……';
        try {
            const participants = await fetchJson(`/activities/${editingActivityId}/participants`);
            const ids = new Set(participants.map((member) => String(member.id)));
            editorParticipantInputs().forEach((input) => { input.checked = ids.has(input.value); });
            if (editorStatus) editorStatus.textContent = '';
        } catch (error) {
            if (editorStatus) editorStatus.textContent = error.message;
        }
    };

    const openParticipants = async (row) => {
        currentActivityId = row.dataset.id;
        document.querySelector('#participant-modal-title').textContent = `${row.dataset.title} · 活动参与人`;
        participantInputs().forEach((input) => { input.checked = false; });
        resetMemberFilters();
        document.querySelector('#participant-status').textContent = '';
        toggle(participantModal, true);
        try {
            const participants = await fetchJson(`/activities/${currentActivityId}/participants`);
            const ids = new Set(participants.map((member) => String(member.id)));
            participantInputs().forEach((input) => { input.checked = ids.has(input.value); });
        } catch (error) {
            document.querySelector('#participant-status').textContent = error.message;
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
        const [participantsResult, imagesResult] = await Promise.all([
            fetchJson(`/activities/${currentActivityId}/participants`).then((data) => ({ data })).catch((error) => ({ error })),
            fetchJson(`/activities/${currentActivityId}/images`).then((data) => ({ data })).catch((error) => ({ error }))
        ]);
        if (participantsResult.error) {
            document.querySelector('#detail-count').textContent = '读取失败';
            list.innerHTML = `<p class="detail-empty">${escapeHtml(participantsResult.error.message)}</p>`;
        } else {
            const participants = participantsResult.data;
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
        }
        if (imagesResult.error) {
            document.querySelector('#detail-image-count').textContent = '读取失败';
            imageList.innerHTML = `<p class="detail-empty">${escapeHtml(imagesResult.error.message)}</p>`;
        } else {
            renderDetailImages(imagesResult.data);
        }
    };

    const renderExistingImages = (images) => {
        const list = document.querySelector('#image-existing-list');
        list.innerHTML = '';
        if (!images.length) {
            list.innerHTML = '<p class="detail-empty">暂未上传活动图片。</p>';
            return;
        }
        images.forEach((item) => {
            const tile = document.createElement('div');
            tile.className = 'image-preview-tile existing-image-tile';
            tile.dataset.fileId = item.id;
            const image = document.createElement('img');
            image.src = item.url;
            image.alt = item.originalName || '活动图片';
            const footer = document.createElement('div');
            footer.className = 'existing-image-footer';
            const name = document.createElement('small');
            name.textContent = item.originalName || '活动图片';
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'image-remove-button';
            remove.dataset.fileId = item.id;
            remove.textContent = '删除';
            remove.setAttribute('aria-label', `删除${item.originalName || '活动图片'}`);
            footer.append(name, remove);
            tile.append(image, footer);
            list.append(tile);
        });
    };

    const openImageUpload = async (row) => {
        currentActivityId = row.dataset.id;
        document.querySelector('#image-modal-title').textContent = `${row.dataset.title} · 活动图片`;
        resetInput(uploadImageInput);
        clearPreview(uploadPreview, uploadPreviewUrls);
        uploadStatus.textContent = '正在加载已有图片……';
        document.querySelector('#image-existing-list').innerHTML = '<p class="detail-loading">正在加载图片……</p>';
        toggle(imageModal, true);
        try {
            const images = await fetchJson(`/activities/${currentActivityId}/images`);
            renderExistingImages(images);
            uploadStatus.textContent = '';
        } catch (error) {
            uploadStatus.textContent = error.message;
            document.querySelector('#image-existing-list').innerHTML = '<p class="detail-empty">已有图片暂时无法读取。</p>';
        }
    };

    document.querySelector('[data-open-activity-modal]')?.addEventListener('click', () => openActivity());
    document.querySelectorAll('.edit-activity-button').forEach((button) => button.addEventListener('click', () => openActivity(button.closest('.activity-row'))));
    document.querySelectorAll('.participant-button').forEach((button) => button.addEventListener('click', () => openParticipants(button.closest('.activity-row'))));
    document.querySelectorAll('.detail-button').forEach((button) => button.addEventListener('click', () => openDetails(button.closest('.activity-row'))));
    document.querySelectorAll('.image-button').forEach((button) => button.addEventListener('click', () => openImageUpload(button.closest('.activity-row'))));
    document.querySelectorAll('.delete-activity-button').forEach((button) => button.addEventListener('click', async (event) => {
        event.preventDefault();
        const row = button.closest('.activity-row');
        const activityId = row?.dataset.id;
        if (!activityId || !window.confirm('确定删除这条活动记录吗？关联参与人和活动图片也会删除。')) return;
        button.disabled = true;
        try {
            await fetchJson(`/api/activities/${activityId}`, { method: 'DELETE' });
            row.remove();
            window.location.reload();
        } catch (error) {
            button.disabled = false;
            window.alert(error.message);
        }
    }));
    document.querySelectorAll('[data-close-activity-modal]').forEach((button) => button.addEventListener('click', () => { resetEditor(); toggle(activityModal, false); }));
    document.querySelectorAll('[data-close-participant-modal]').forEach((button) => button.addEventListener('click', () => toggle(participantModal, false)));
    document.querySelectorAll('[data-close-detail-modal]').forEach((button) => button.addEventListener('click', () => toggle(detailModal, false)));
    document.querySelectorAll('[data-close-image-modal]').forEach((button) => button.addEventListener('click', () => { clearPreview(uploadPreview, uploadPreviewUrls); resetInput(uploadImageInput); toggle(imageModal, false); }));
    document.querySelector('[data-close-lightbox]')?.addEventListener('click', closeLightbox);
    lightbox?.addEventListener('click', (event) => { if (event.target === lightbox) closeLightbox(); });
    [activityModal, participantModal, detailModal, imageModal].forEach((modal) => modal?.addEventListener('click', (event) => {
        if (event.target !== modal) return;
        if (modal === activityModal) resetEditor();
        if (modal === imageModal) { clearPreview(uploadPreview, uploadPreviewUrls); resetInput(uploadImageInput); }
        toggle(modal, false);
    }));
    document.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape') return;
        if (activityModal?.classList.contains('is-open')) resetEditor();
        if (imageModal?.classList.contains('is-open')) { clearPreview(uploadPreview, uploadPreviewUrls); resetInput(uploadImageInput); }
        [activityModal, participantModal, detailModal, imageModal].forEach((modal) => toggle(modal, false));
        closeLightbox();
    });

    memberSearchInputs().forEach((input) => input.addEventListener('input', () => applyMemberFilter(input)));
    resetMemberFilters();

    editorImageInput?.addEventListener('change', (event) => {
        const files = Array.from(event.target.files || []);
        const error = validateImages(files, 8);
        clearPreview(editorPreview, editorPreviewUrls);
        if (error) {
            editorStatus.textContent = error;
            resetInput(editorImageInput);
            return;
        }
        editorStatus.textContent = files.length ? `已选择 ${files.length} 张图片。` : '';
        renderLocalPreviews(files, editorPreview, editorPreviewUrls);
    });

    uploadImageInput?.addEventListener('change', (event) => {
        const files = Array.from(event.target.files || []);
        const error = validateImages(files, 8);
        clearPreview(uploadPreview, uploadPreviewUrls);
        if (error) {
            uploadStatus.textContent = error;
            resetInput(uploadImageInput);
            return;
        }
        uploadStatus.textContent = files.length ? `已选择 ${files.length} 张图片，确认预览无误后上传。` : '';
        renderLocalPreviews(files, uploadPreview, uploadPreviewUrls);
    });

    activityForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const submit = document.querySelector('#activity-submit');
        const files = Array.from(editorImageInput?.files || []);
        const validationError = validateImages(files, 8);
        if (validationError) { editorStatus.textContent = validationError; return; }
        submit.disabled = true;
        editorStatus.textContent = '正在保存活动信息……';
        try {
            const body = new FormData();
            Object.values(activityFields).forEach((field) => body.append(field.name, field.value));
            const endpoint = editingActivityId ? `/api/activities/${editingActivityId}` : '/api/activities';
            const method = editingActivityId ? 'PUT' : 'POST';
            const saved = await fetchJson(endpoint, { method, body });
            const activityId = saved.id || editingActivityId;
            await replaceParticipants(activityId, editorParticipantInputs());
            if (files.length) await uploadImages(activityId, files);
            clearPreview(editorPreview, editorPreviewUrls);
            resetInput(editorImageInput);
            window.location.reload();
        } catch (error) {
            editorStatus.textContent = error.message;
            submit.disabled = false;
        }
    });

    document.querySelector('#image-existing-list')?.addEventListener('click', async (event) => {
        const button = event.target.closest('.image-remove-button');
        if (!button) return;
        if (!window.confirm('确定删除这张活动图片吗？')) return;
        button.disabled = true;
        try {
            const response = await fetch(`/activities/files/${button.dataset.fileId}`, { method: 'DELETE' });
            await responseJson(response);
            button.closest('.existing-image-tile')?.remove();
            if (!document.querySelector('#image-existing-list .existing-image-tile')) {
                document.querySelector('#image-existing-list').innerHTML = '<p class="detail-empty">暂未上传活动图片。</p>';
            }
            uploadStatus.textContent = '图片已删除。';
        } catch (error) {
            button.disabled = false;
            uploadStatus.textContent = error.message;
        }
    });

    document.querySelector('#upload-images-submit')?.addEventListener('click', async () => {
        const files = Array.from(uploadImageInput?.files || []);
        if (!currentActivityId) return;
        const validationError = validateImages(files, 8);
        if (validationError) { uploadStatus.textContent = validationError; return; }
        if (!files.length) { uploadStatus.textContent = '请先选择活动图片。'; return; }
        const submit = document.querySelector('#upload-images-submit');
        submit.disabled = true;
        uploadStatus.textContent = '正在上传图片，请稍候……';
        try {
            const images = await uploadImages(currentActivityId, files);
            renderExistingImages(images);
            clearPreview(uploadPreview, uploadPreviewUrls);
            resetInput(uploadImageInput);
            uploadStatus.textContent = `上传完成，共保存 ${images.length} 张活动图片。`;
        } catch (error) {
            uploadStatus.textContent = error.message;
        } finally {
            submit.disabled = false;
        }
    });

    participantForm?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const status = document.querySelector('#participant-status');
        try {
            const result = await replaceParticipants(currentActivityId, participantInputs());
            updateCount(result.participantCount);
            status.textContent = '参与人已保存。';
        } catch (error) {
            status.textContent = error.message;
        }
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

    function closeLightbox() {
        toggle(lightbox, false);
        document.querySelector('#lightbox-image')?.removeAttribute('src');
    }

    function updateCount(count) {
        const element = document.querySelector(`[data-count-id="${currentActivityId}"]`);
        if (element) element.textContent = `${count} 人`;
    }

    function escapeHtml(value) {
        return String(value).replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[character]));
    }
});
