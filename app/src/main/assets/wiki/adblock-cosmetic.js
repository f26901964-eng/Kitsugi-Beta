/* Narrow ad containers only; never hide generic banners, videos, galleries or wiki tables. */
(function () {
    'use strict';
    if (window.__kitsugiWikiAdsV1) return;
    var css = '.top-ads-container,.bottom-ads-container,.in-article-ad,.wikia-ad,.gpt-ad,.ad-slot,' +
        '#top_leaderboard,#bottom_leaderboard,#incontent_boxad_1,#incontent_boxad_2,' +
        '#incontent_boxad_3,[id^="div-gpt-ad"],ins.adsbygoogle' +
        '{display:none!important;min-height:0!important;height:0!important;margin:0!important;padding:0!important;}';
    var id = 'kitsugi-wiki-ad-style-v1';
    window.__kitsugiWikiAdsV1 = function (enabled) {
        var style = document.getElementById(id);
        if (!enabled) { if (style) style.remove(); return; }
        if (!style) { style = document.createElement('style'); style.id = id; document.head.appendChild(style); }
        if (style.textContent !== css) style.textContent = css;
    };
})();
