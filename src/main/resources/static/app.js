/* Allegro Lister - interakcje panelu (bez zewnętrznych bibliotek). */
(function () {
    'use strict';

    const $ = (sel, root = document) => root.querySelector(sel);
    const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

    async function getJson(url) {
        const res = await fetch(url, { headers: { Accept: 'application/json' } });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(data.error || ('HTTP ' + res.status));
        }
        return data;
    }

    /* ---------- potwierdzenia ---------- */
    document.addEventListener('click', (e) => {
        const el = e.target.closest('[data-confirm]');
        if (el && !window.confirm(el.dataset.confirm)) {
            e.preventDefault();
            e.stopImmediatePropagation();
        }
    }, true);

    /* ---------- zaznacz wszystkie ---------- */
    $$('[data-check-all]').forEach((master) => {
        master.addEventListener('change', () => {
            $$('input[type=checkbox][name="' + master.dataset.checkAll + '"]').forEach((cb) => {
                if (!cb.disabled) cb.checked = master.checked;
            });
        });
    });

    /* ---------- licznik znaków tytułu (zasady Allegro: & = 5 znaków itd.) ---------- */
    function allegroLength(text) {
        let len = 0;
        for (const ch of text) {
            len += ch === '&' ? 5 : ch === '"' ? 6 : (ch === '<' || ch === '>') ? 4 : 1;
        }
        return len;
    }
    $$('.title-input').forEach((input) => {
        const counter = input.closest('label') && input.closest('label').querySelector('.counter');
        if (!counter) return;
        const update = () => {
            const n = allegroLength(input.value);
            counter.textContent = n + '/75';
            counter.classList.toggle('over', n > 75);
        };
        input.addEventListener('input', update);
        update();
    });

    /* ---------- wybór kategorii Allegro ---------- */
    const modal = $('#cat-modal');
    let activePicker = null;

    function pickerAccount(picker) {
        if (picker.dataset.account) return picker.dataset.account;
        const holder = picker.closest('[data-account]');
        if (holder && holder.dataset.account) return holder.dataset.account;
        const select = $('#picker-account');
        return select ? select.value : null;
    }

    function openModal(title) {
        $('#cat-modal-title').textContent = title;
        modal.hidden = false;
    }

    function closeModal() {
        modal.hidden = true;
        activePicker = null;
    }

    function choose(id, name) {
        if (!activePicker) return;
        const idInput = $('.cat-id', activePicker);
        const nameInput = $('.cat-name', activePicker);
        if (idInput) idInput.value = id;
        if (nameInput) nameInput.value = name;
        idInput && idInput.dispatchEvent(new Event('change', { bubbles: true }));
        closeModal();
    }

    function renderList(items, onClick) {
        const list = $('#cat-list');
        list.innerHTML = '';
        if (!items.length) {
            list.innerHTML = '<div class="empty">Brak wyników</div>';
            return;
        }
        items.forEach((item) => {
            const b = document.createElement('button');
            b.type = 'button';
            const label = document.createElement('span');
            label.textContent = item.name;
            const mark = document.createElement('span');
            mark.className = item.leaf ? 'leaf' : 'muted';
            mark.textContent = item.leaf ? '✓' : '›';
            b.append(label, mark);
            b.addEventListener('click', () => onClick(item));
            list.appendChild(b);
        });
    }

    async function browse(account, path) {
        const crumbs = $('#cat-breadcrumbs');
        crumbs.innerHTML = '';
        const root = document.createElement('a');
        root.textContent = 'Allegro';
        root.addEventListener('click', () => browse(account, []));
        crumbs.appendChild(root);
        path.forEach((node, i) => {
            crumbs.append(' › ');
            const a = document.createElement('a');
            a.textContent = node.name;
            a.addEventListener('click', () => browse(account, path.slice(0, i + 1)));
            crumbs.appendChild(a);
        });
        $('#cat-list').innerHTML = '<div class="empty">Ładowanie…</div>';
        const parent = path.length ? path[path.length - 1].id : '';
        try {
            const items = await getJson('/api/categories?accountId=' + encodeURIComponent(account) +
                (parent ? '&parentId=' + encodeURIComponent(parent) : ''));
            renderList(items, (item) => {
                const newPath = path.concat([item]);
                if (item.leaf) {
                    choose(item.id, newPath.map((n) => n.name).join(' > '));
                } else {
                    browse(account, newPath);
                }
            });
        } catch (err) {
            $('#cat-list').innerHTML = '';
            const div = document.createElement('div');
            div.className = 'empty err-text';
            div.textContent = err.message;
            $('#cat-list').appendChild(div);
        }
    }

    async function match(account, name) {
        $('#cat-breadcrumbs').textContent = 'Propozycje Allegro dla: ' + name;
        $('#cat-list').innerHTML = '<div class="empty">Szukam…</div>';
        try {
            const items = await getJson('/api/categories/match?accountId=' + encodeURIComponent(account) +
                '&name=' + encodeURIComponent(name));
            renderList(items.map((c) => ({ id: c.id, name: c.name, leaf: true })), (item) => choose(item.id, item.name));
        } catch (err) {
            $('#cat-list').innerHTML = '';
            const div = document.createElement('div');
            div.className = 'empty err-text';
            div.textContent = err.message;
            $('#cat-list').appendChild(div);
        }
    }

    document.addEventListener('click', (e) => {
        const browseBtn = e.target.closest('[data-cat-browse]');
        const matchBtn = e.target.closest('[data-cat-match]');
        if (!browseBtn && !matchBtn) {
            if (e.target.closest('[data-close-modal]') || e.target === modal) closeModal();
            return;
        }
        e.preventDefault();
        if (!modal) return;
        activePicker = (browseBtn || matchBtn).closest('.cat-picker');
        const account = pickerAccount(activePicker);
        if (!account) {
            alert('Najpierw połącz konto Allegro.');
            return;
        }
        if (browseBtn) {
            openModal('Wybierz kategorię Allegro');
            browse(account, []);
        } else {
            let name = activePicker.dataset.name || '';
            const from = matchBtn.dataset.nameFrom;
            if (from) {
                const field = matchBtn.form && matchBtn.form.querySelector('[name="' + from + '"]');
                name = field ? field.value : name;
            }
            const titleInput = activePicker.closest('.item') && $('.title-input', activePicker.closest('.item'));
            if (titleInput && titleInput.value) name = titleInput.value;
            if (!name) {
                alert('Brak nazwy do dopasowania.');
                return;
            }
            openModal('Dopasuj kategorię');
            match(account, name);
        }
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && modal && !modal.hidden) closeModal();
    });

    /* ---------- pola zależne od wybranej operacji ---------- */
    function bindSwitch(select, attr) {
        if (!select) return;
        const update = () => {
            const value = select.value;
            $$('[' + attr + ']', select.form || document).forEach((box) => {
                const active = box.getAttribute(attr).split(' ').includes(value);
                box.hidden = !active;
                $$('input, select, textarea', box).forEach((el) => { el.disabled = !active; });
            });
        };
        select.addEventListener('change', update);
        update();
    }
    bindSwitch($('#bulk-field'), 'data-bulk-for');
    bindSwitch($('#offer-operation'), 'data-op-for');

    const ruleType = $('#rule-type');
    if (ruleType) {
        const update = () => {
            $$('[data-rule-for]').forEach((el) => {
                el.hidden = !el.dataset.ruleFor.split(' ').includes(ruleType.value);
            });
        };
        ruleType.addEventListener('change', update);
        update();
    }

    /* ---------- reguły: podpowiedzi parametrów kategorii ---------- */
    const loadParams = $('#load-params');
    if (loadParams) {
        let params = [];
        const nameInput = $('#rule-param-name');
        const idInput = $('#rule-param-id');
        nameInput.addEventListener('input', () => {
            const found = params.find((p) => p.name === nameInput.value);
            idInput.value = found ? found.id : '';
        });
        loadParams.addEventListener('click', async () => {
            const category = $('#rule-category').value.trim();
            const account = $('#picker-account') && $('#picker-account').value;
            if (!category || !account) {
                alert('Wybierz kategorię Allegro (i konto), żeby pobrać listę parametrów.');
                return;
            }
            loadParams.disabled = true;
            try {
                params = await getJson('/api/categories/' + encodeURIComponent(category) + '/parameters?accountId=' + encodeURIComponent(account));
                const list = $('#category-params');
                list.innerHTML = '';
                params.forEach((p) => {
                    const o = document.createElement('option');
                    o.value = p.name;
                    o.label = (p.required ? '* ' : '') + p.type + (p.describesProduct ? ', produktowy' : ', ofertowy');
                    list.appendChild(o);
                });
                nameInput.focus();
            } catch (err) {
                alert('Nie udało się pobrać parametrów: ' + err.message);
            } finally {
                loadParams.disabled = false;
            }
        });
    }

    /* ---------- edytor szablonu ---------- */
    const sections = $('#sections');
    if (sections) {
        const form = $('#template-form');
        const tpl = $('#section-template');

        const applyLayout = (block) => {
            const layout = $('.layout-select', block).value;
            const hasImage = layout !== 'TEXT';
            const hasText = layout === 'TEXT' || layout === 'IMAGE_TEXT' || layout === 'TEXT_IMAGE';
            $('.f-image1', block).hidden = !hasImage;
            $('.f-image2', block).hidden = layout !== 'IMAGE_IMAGE';
            $('.f-text', block).hidden = !hasText;
        };

        const renumber = () => {
            $$('.section-block', sections).forEach((block, i) => {
                $('.section-no', block).textContent = 'Sekcja ' + (i + 1);
                $$('[name]', block).forEach((el) => {
                    el.name = el.name.replace(/sections\[[^\]]*\]/, 'sections[' + i + ']');
                });
            });
        };

        sections.addEventListener('change', (e) => {
            if (e.target.classList.contains('layout-select')) applyLayout(e.target.closest('.section-block'));
        });
        sections.addEventListener('click', (e) => {
            const block = e.target.closest('.section-block');
            if (!block) return;
            if (e.target.closest('[data-remove-section]')) {
                block.remove();
                renumber();
            } else if (e.target.closest('[data-move="up"]') && block.previousElementSibling) {
                sections.insertBefore(block, block.previousElementSibling);
                renumber();
            } else if (e.target.closest('[data-move="down"]') && block.nextElementSibling) {
                sections.insertBefore(block.nextElementSibling, block);
                renumber();
            }
        });
        $('#add-section').addEventListener('click', () => {
            const node = tpl.content.firstElementChild.cloneNode(true);
            sections.appendChild(node);
            renumber();
            applyLayout(node);
            node.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
        form.addEventListener('submit', renumber);
        $$('.section-block', sections).forEach(applyLayout);
    }

    /* ---------- formularz wystawiania: odświeżanie statusów ---------- */
    const listingForm = $('#listing-form');
    if (listingForm && listingForm.dataset.job) {
        const busy = ['QUEUED', 'PROCESSING', 'PENDING'];
        const classes = { ACTIVE: 'ok', ERROR: 'err', INACTIVE: 'warn', QUEUED: 'busy', PROCESSING: 'busy', PENDING: 'busy' };
        const hasBusy = () => $$('[data-status-for]').some((b) => /W kolejce|Wysyłanie|Weryfikacja/.test(b.textContent));
        let timer = null;

        const poll = async () => {
            try {
                const rows = await getJson('/api/listing/' + listingForm.dataset.job + '/status');
                let stillBusy = false;
                let changedToFinal = false;
                rows.forEach((row) => {
                    const badge = $('[data-status-for="' + row.id + '"]');
                    if (!badge) return;
                    const wasBusy = /W kolejce|Wysyłanie|Weryfikacja/.test(badge.textContent);
                    badge.textContent = row.label;
                    badge.className = 'badge ' + (classes[row.status] || '');
                    const msg = $('[data-message-for="' + row.id + '"]');
                    if (msg) {
                        msg.textContent = row.message || '';
                        msg.classList.toggle('err-text', row.status === 'ERROR');
                    }
                    if (busy.includes(row.status)) stillBusy = true;
                    else if (wasBusy) changedToFinal = true;
                });
                if (!stillBusy) {
                    clearInterval(timer);
                    if (changedToFinal) {
                        // przeładowanie odblokowuje edycję pozycji z błędami i pokazuje linki do ofert
                        setTimeout(() => window.location.reload(), 800);
                    }
                }
            } catch (err) {
                clearInterval(timer);
            }
        };
        if (hasBusy()) {
            timer = setInterval(poll, 4000);
        }
    }
})();
