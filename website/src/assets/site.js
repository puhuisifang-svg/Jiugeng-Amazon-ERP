const menu = document.querySelector('.menu');
const nav = document.querySelector('#nav');
const links = [...nav.querySelectorAll('a')];
const sections = links.map(link => document.querySelector(link.hash));
const mobile = window.matchMedia('(max-width: 760px)');

function setMenu(open) {
  menu.hidden = !mobile.matches;
  menu.setAttribute('aria-expanded', String(open));
  menu.textContent = open ? '关闭菜单' : '菜单';
  nav.hidden = mobile.matches && !open;
}

menu.addEventListener('click', () => setMenu(nav.hidden));
mobile.addEventListener('change', () => setMenu(false));
setMenu(false);

document.querySelectorAll('a[href^="#"]').forEach(link => {
  link.addEventListener('click', () => {
    const wasOpen = mobile.matches && !nav.hidden;
    setMenu(false);
    if (wasOpen) {
      const section = document.querySelector(link.hash);
      section.tabIndex = -1;
      section.focus({ preventScroll: true });
    }
  });
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && mobile.matches && !nav.hidden) {
    setMenu(false);
    menu.focus();
  }
});

function markSection() {
  const headerBottom = document.querySelector('header').getBoundingClientRect().bottom;
  const reachedEnd = window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2;
  let current = sections[0];
  for (const section of sections) {
    if (section.getBoundingClientRect().top <= headerBottom + 20 || reachedEnd) current = section;
  }
  for (const link of links) {
    if (link.hash === '#' + current.id) link.setAttribute('aria-current', 'location');
    else link.removeAttribute('aria-current');
  }
}

window.addEventListener('scroll', markSection, { passive: true });
window.addEventListener('resize', markSection);
window.addEventListener('pageshow', markSection);
markSection();

const copyStatus = document.querySelector('#copy-status');
for (const button of document.querySelectorAll('[data-copy]')) {
  const link = document.getElementById(button.dataset.copy);
  button.hidden = false;
  button.addEventListener('click', async () => {
    const value = link.textContent.trim();
    try {
      await navigator.clipboard.writeText(value);
      copyStatus.textContent = button.getAttribute('aria-label').replace('复制', '') + '已复制';
    } catch {
      const range = document.createRange();
      range.selectNodeContents(link);
      const selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      copyStatus.textContent = '请手动复制选中的联系方式';
    }
  });
}
