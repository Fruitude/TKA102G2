(() => {
    document.querySelectorAll('.sku-switcher').forEach(switcher => {
        const names = Array.from(switcher.querySelectorAll('.sku-name-options > span'), item => item.textContent);
        const button = switcher.querySelector('.sku-counter');
        const track = switcher.querySelector('.sku-name-track');
        let index = 0;
        button.addEventListener('click', () => {
            if (button.disabled || names.length < 2) return;
            button.disabled = true;
            const nextIndex = (index + 1) % names.length;
            const incoming = document.createElement('span');
            incoming.className = 'sku-name';
            const characters = Array.from(names[nextIndex]);
            incoming.textContent = characters.length > 8 ? characters.slice(0, 8).join('') + '…' : names[nextIndex];
            incoming.title = names[nextIndex];
            incoming.setAttribute('aria-hidden', 'true');
            track.appendChild(incoming);
            let finished = false;
            let fallback;
            const finish = () => {
                if (finished) return;
                finished = true;
                clearTimeout(fallback);
                track.removeEventListener('transitionend', onTransitionEnd);
                track.firstElementChild.remove();
                track.classList.remove('is-sliding-up');
                incoming.removeAttribute('aria-hidden');
                index = nextIndex;
                button.textContent = (index + 1) + '/' + names.length;
                button.setAttribute('aria-label', '切換下一個規格，目前第' + (index + 1) + '個，共' + names.length + '個');
                button.disabled = false;
            };
            const onTransitionEnd = event => {
                if (event.target === track && event.propertyName === 'transform') finish();
            };
            track.addEventListener('transitionend', onTransitionEnd);
            track.getBoundingClientRect();
            if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) finish();
            else {
                track.classList.add('is-sliding-up');
                fallback = setTimeout(finish, 350);
            }
        });
    });
})();
