precision mediump float;

varying vec2 vTexCoord;
uniform sampler2D sTexture;

// Blend rectangle in normalized screen coords (minX, minY, maxX, maxY)
uniform vec4 uBlendRect;
// The alpha value to use inside the rectangle (e.g., 0.5 for 50% transparent)
uniform float uBlendAlpha;
//To hold the view's width and height
uniform vec2 uResolution;

void main() {
    vec4 imageColor = texture2D(sTexture, vTexCoord);

    // Calculate normalized screen coordinates directly from gl_FragCoord
    vec2 normalizedScreenCoord = gl_FragCoord.xy / uResolution.xy;

    // Check if the rectangle is valid (width > 0)
    if (uBlendRect.z > uBlendRect.x) {
        bool inRectX = normalizedScreenCoord.x > uBlendRect.x && normalizedScreenCoord.x < uBlendRect.z;
        bool inRectY = normalizedScreenCoord.y > uBlendRect.y && normalizedScreenCoord.y < uBlendRect.w;

        if (inRectX && inRectY) {
            imageColor.a = uBlendAlpha;
        } else {
            imageColor.a = 1.0; // Fully opaque outside the rectangle
        }
    }
    
    gl_FragColor = imageColor;
}

