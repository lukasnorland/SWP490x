/* ==========================================================================
   Cover art -> ambience colors.
   Samples a song cover into 1-2 wash colors for the shell ambience layer.
   ========================================================================== */
"use strict";

function rgbToCss(r, g, b, alpha) {
  return "rgba(" + Math.round(r) + ", " + Math.round(g) + ", " + Math.round(b) + ", " + alpha + ")";
}

function darkenForWash(r, g, b) {
  return {
    r: r * 0.45 + 8,
    g: g * 0.45 + 8,
    b: b * 0.45 + 12
  };
}

export function extractCoverAmbience(coverUrl) {
  return new Promise(function (resolve, reject) {
    if (!coverUrl) {
      reject(new Error("no cover"));
      return;
    }
    var image = new Image();
    image.crossOrigin = "anonymous";
    image.onload = function () {
      try {
        var size = 32;
        var canvas = document.createElement("canvas");
        canvas.width = size;
        canvas.height = size;
        var ctx = canvas.getContext("2d", { willReadFrequently: true });
        if (!ctx) {
          reject(new Error("no canvas"));
          return;
        }
        ctx.drawImage(image, 0, 0, size, size);
        var data = ctx.getImageData(0, 0, size, size).data;
        var buckets = {};
        var i;
        for (i = 0; i < data.length; i += 4) {
          var a = data[i + 3];
          if (a < 200) {
            continue;
          }
          var r = data[i];
          var g = data[i + 1];
          var b = data[i + 2];
          var max = Math.max(r, g, b);
          var min = Math.min(r, g, b);
          var lightness = (max + min) / 2;
          var saturation = max === 0 ? 0 : (max - min) / max;
          // Skip near-black / near-white / grey pixels for a clearer wash.
          if (lightness < 28 || lightness > 230 || saturation < 0.12) {
            continue;
          }
          var key = (r >> 4) + "," + (g >> 4) + "," + (b >> 4);
          if (!buckets[key]) {
            buckets[key] = { count: 0, r: 0, g: 0, b: 0 };
          }
          buckets[key].count += 1;
          buckets[key].r += r;
          buckets[key].g += g;
          buckets[key].b += b;
        }

        var ranked = Object.keys(buckets).map(function (key) {
          var bucket = buckets[key];
          return {
            count: bucket.count,
            r: bucket.r / bucket.count,
            g: bucket.g / bucket.count,
            b: bucket.b / bucket.count
          };
        }).sort(function (left, right) {
          return right.count - left.count;
        });

        if (!ranked.length) {
          // Fallback: whole-image average if filters removed everything.
          var totalR = 0;
          var totalG = 0;
          var totalB = 0;
          var pixels = 0;
          for (i = 0; i < data.length; i += 4) {
            if (data[i + 3] < 200) {
              continue;
            }
            totalR += data[i];
            totalG += data[i + 1];
            totalB += data[i + 2];
            pixels += 1;
          }
          if (!pixels) {
            reject(new Error("empty image"));
            return;
          }
          ranked = [{
            count: pixels,
            r: totalR / pixels,
            g: totalG / pixels,
            b: totalB / pixels
          }];
        }

        var primary = darkenForWash(ranked[0].r, ranked[0].g, ranked[0].b);
        var secondarySource = ranked[1] || ranked[0];
        var secondary = darkenForWash(secondarySource.r, secondarySource.g, secondarySource.b);
        resolve({
          a: rgbToCss(primary.r, primary.g, primary.b, 0.9),
          b: rgbToCss(secondary.r, secondary.g, secondary.b, 0.75)
        });
      } catch (error) {
        reject(error);
      }
    };
    image.onerror = function () {
      reject(new Error("cover load failed"));
    };
    image.src = coverUrl;
  });
}
