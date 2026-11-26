'use strict';

const element = id => document.getElementById(id);
let csrfToken;
let questionnaire;
let excluded = [];

function textElement(tag, text) {
    const child = document.createElement(tag);
    child.textContent = text;
    return child;
}

async function request(method, path, body) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET' && csrfToken) headers['X-CSRF-TOKEN'] = csrfToken;
    const response = await fetch('/api/v1' + path, {
        method, headers, body: JSON.stringify(body), cache: 'no-store'
    });
    const data = await response.json();
    const visible = { ...data };
    delete visible.csrfToken;
    const lines = [method + ' ' + path + ' → ' + response.status];
    if (body && path !== '/auth/session') lines.push(JSON.stringify(body, null, 2));
    lines.push(JSON.stringify(visible, null, 2));
    element('history').prepend(textElement('pre', lines.join('\n')));
    if (!response.ok) throw Error(lines[0] + ' · ' + (data.message || data.code || response.statusText));
    return data;
}

function busy(action) {
    return async event => {
        event?.preventDefault();
        const buttons = document.querySelectorAll('button');
        buttons.forEach(button => { button.disabled = true; });
        try {
            await action();
        } catch (error) {
            element('status').textContent = error.message;
        } finally {
            buttons.forEach(button => { button.disabled = false; });
        }
    };
}

function choice(id) {
    return element(id).querySelector('input:checked')?.value;
}

function updateDrinkQuestion() {
    const field = element('Q4');
    field.hidden = choice('Q1') !== 'A';
    if (field.hidden) field.querySelectorAll('input').forEach(input => { input.checked = false; });
}

async function showSurvey() {
    questionnaire = await request('GET', '/preference-questionnaire');
    if (questionnaire.version !== 'v0.1.0') throw Error('지원하지 않는 설문 버전 ' + questionnaire.version);
    const saved = await request('GET', '/me/preferences');
    const fields = questionnaire.questions.map(question => {
        const field = document.createElement('fieldset');
        field.id = question.id;
        field.append(textElement('legend', question.label));
        const selected = saved.questionnaireVersion === questionnaire.version
            && saved.answers.find(answer => answer.questionId === question.id);
        for (const option of question.options) {
            const label = document.createElement('label');
            const input = document.createElement('input');
            input.type = 'radio';
            input.name = question.id;
            input.value = option.id;
            input.checked = Boolean(selected && selected.optionIds.includes(option.id));
            label.append(input, document.createTextNode(option.label));
            field.append(label);
        }
        return field;
    });
    element('questions').replaceChildren(...fields);
    updateDrinkQuestion();
    element('login').hidden = true;
    element('survey').hidden = false;
    element('status').textContent = '로그인 완료 · 설문 ' + questionnaire.version;
}

async function recommend() {
    const { menu } = await request('POST', '/menu-recommendations', { excludedMenuIds: excluded });
    const card = element('card');
    card.replaceChildren();
    element('result').hidden = false;
    if (!menu) {
        card.append(textElement('p', '더 추천할 메뉴가 없어요.'));
        element('next').hidden = true;
        return;
    }
    excluded.push(menu.id);
    element('next').hidden = excluded.length > 100;
    card.append(textElement('strong', menu.name), textElement('p', menu.description || ''),
        textElement('p', [menu.cafe.name, menu.cafe.roadAddress].filter(Boolean).join(' · ')));
    if (menu.cafe.directionsUrl?.startsWith('https://map.naver.com/')) {
        const link = textElement('a', '네이버 지도 길찾기 ↗');
        link.href = menu.cafe.directionsUrl;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        card.append(link);
    }
    element('status').textContent = '저장된 취향으로 받은 ' + excluded.length + '번째 추천이에요.';
    element('result').scrollIntoView({ block: 'center' });
}

element('login').onsubmit = busy(async () => {
    const session = await request('GET', '/auth/session');
    csrfToken = session.csrfToken;
    if (!session.authenticated) {
        const login = await request('POST', '/auth/session', { loginId: 'fe-demo', password: element('password').value });
        csrfToken = login.csrfToken;
    }
    element('password').value = '';
    await showSurvey();
});
element('form').onchange = updateDrinkQuestion;
element('form').onsubmit = busy(async () => {
    const answers = questionnaire.questions.map(({ id }) => ({
        questionId: id, optionIds: choice(id) ? [choice(id)] : []
    }));
    await request('PUT', '/me/preferences', { questionnaireVersion: questionnaire.version, answers });
    excluded = [];
    element('result').hidden = true;
    await recommend();
});
element('next').onclick = busy(recommend);
busy(async () => {
    const session = await request('GET', '/auth/session');
    csrfToken = session.csrfToken;
    if (session.authenticated) await showSurvey();
})();
