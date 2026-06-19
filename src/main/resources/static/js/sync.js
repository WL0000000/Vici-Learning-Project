// Sync page interactions:
//  1) "Syncing…" state on the Sync Now button while the POST runs
//  2) Rolling count-up animation on stat cards whose value changed since last visit
(function () {
    "use strict";

    var reduceMotion = window.matchMedia
        && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    // ── 1) Sync Now button: show progress while the form POST is in flight ──────
    var form = document.getElementById("sync-form");
    if (form) {
        form.addEventListener("submit", function () {
            var btn = document.getElementById("sync-btn");
            if (!btn) return;
            btn.disabled = true;
            btn.classList.add("is-syncing");
            btn.innerHTML = '<span class="btn-spinner" aria-hidden="true"></span>Syncing…';
        });
    }

    // ── 2) Animate only the counts that actually changed ────────────────────────
    var STORE_KEY = "vici.statCounts";

    var prev = {};
    try {
        prev = JSON.parse(localStorage.getItem(STORE_KEY) || "{}") || {};
    } catch (e) {
        prev = {};
    }

    var current = {};
    var nodes = document.querySelectorAll(".stat-val[data-stat]");

    nodes.forEach(function (el) {
        var key = el.getAttribute("data-stat");
        var target = parseInt(el.textContent.trim(), 10);
        if (isNaN(target)) target = 0;
        current[key] = target;

        var hasBaseline = Object.prototype.hasOwnProperty.call(prev, key);
        var from = hasBaseline ? prev[key] : target;

        if (hasBaseline && from !== target && !reduceMotion) {
            animateCount(el, from, target, 700);
            flashCard(el);
        } else {
            el.textContent = target;
        }
    });

    try {
        localStorage.setItem(STORE_KEY, JSON.stringify(current));
    } catch (e) { /* storage unavailable — animation just won't run next time */ }

    function animateCount(el, from, to, duration) {
        var start = null;
        function step(ts) {
            if (start === null) start = ts;
            var p = Math.min((ts - start) / duration, 1);
            var eased = 1 - Math.pow(1 - p, 3); // easeOutCubic
            el.textContent = Math.round(from + (to - from) * eased);
            if (p < 1) {
                requestAnimationFrame(step);
            } else {
                el.textContent = to;
            }
        }
        requestAnimationFrame(step);
    }

    function flashCard(el) {
        var card = el.closest(".stat-card");
        if (!card) return;
        card.classList.add("stat-changed");
        setTimeout(function () {
            card.classList.remove("stat-changed");
        }, 1200);
    }
})();
