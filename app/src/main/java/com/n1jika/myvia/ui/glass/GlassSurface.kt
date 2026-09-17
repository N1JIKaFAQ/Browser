package com.n1jika.myvia.ui.glass

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AGSL 液态玻璃着色器。
 *
 * 对应 Web 方案里的四个要素：
 *  1. 圆角矩形 SDF —— 决定玻璃轮廓（对应 CSS 圆角 + 抗锯齿）
 *  2. 边缘位移采样 —— 只有靠近边缘的一条带参与弯折（对应 feDisplacementMap 只在边缘 5px 使用）
 *  3. 受光面高光 —— 法线与光向夹角决定亮度（对应边缘高光）
 *  4. 背景模糊 + 色调 —— 磨砂玻璃质感（对应 backdrop-filter: blur）
 * 另外加入绕边框顺时针运动的流光，并且流光经过处会额外提亮边缘高光，形成光效耦合。
 */
private const val GLASS_AGSL = """
uniform shader backdrop;      // 背景（已降采样并模糊）
uniform float2 uSize;         // 玻璃尺寸（px）
uniform float2 uOrigin;       // 玻璃左上角在背景位图中的坐标（px）
uniform float2 uScale;        // 背景位图像素 / 屏幕像素
uniform float  uRadius;       // 圆角半径（px）
uniform float  uRefractBand;  // 参与折射的边缘带宽（px）
uniform float  uRefractAmt;   // 折射强度（px）
uniform float  uInnerBlur;    // 内部补充模糊采样半径（px）
uniform float3 uTint;         // 玻璃色调
uniform float  uTintAlpha;    // 色调占比
uniform float3 uHighlight;    // 边缘高光颜色
uniform float  uHighlightAmt; // 高光强度
uniform float  uProgress;     // 流光进度 0..1（负值表示不显示）
uniform float3 uRimColor;     // 流光颜色
uniform float  uRimTail;      // 流光尾部锐度
uniform float  uRimAlpha;     // 流光整体强度（用于淡入淡出）

const float PI = 3.14159265359;

// 圆角矩形有符号距离场：内部为负，边界为 0，外部为正
float sdRoundRect(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - radius;
}

half4 main(float2 fragCoord) {
    float2 halfSize = uSize * 0.5;
    float radius = min(uRadius, min(halfSize.x, halfSize.y));
    float2 p = fragCoord - halfSize;

    float d = sdRoundRect(p, halfSize, radius);

    // 轮廓抗锯齿：边界 1px 内过渡
    float inside = 1.0 - smoothstep(-1.0, 1.0, d);
    if (inside <= 0.002) {
        return half4(0.0);
    }

    // SDF 梯度即外法线
    float2 n = float2(
        sdRoundRect(p + float2(1.0, 0.0), halfSize, radius) - sdRoundRect(p - float2(1.0, 0.0), halfSize, radius),
        sdRoundRect(p + float2(0.0, 1.0), halfSize, radius) - sdRoundRect(p - float2(0.0, 1.0), halfSize, radius)
    );
    n = normalize(n + float2(1e-6, 1e-6));

    // 边缘折射：越靠边，采样点越沿法线向外偏移，模拟厚玻璃边缘的弯折
    float edgeT = clamp(1.0 + d / max(uRefractBand, 1.0), 0.0, 1.0);
    float bend = edgeT * edgeT * uRefractAmt;
    float2 samplePos = fragCoord - n * bend;

    float2 uv = uOrigin + samplePos * uScale;
    float4 c = float4(backdrop.eval(uv));

    // 内部补充模糊：菱形四抽样
    if (uInnerBlur > 0.01) {
        float2 o = float2(uInnerBlur);
        float4 acc = c;
        acc += float4(backdrop.eval(uv + float2(o.x, o.y)));
        acc += float4(backdrop.eval(uv - float2(o.x, o.y)));
        acc += float4(backdrop.eval(uv + float2(o.x, -o.y)));
        acc += float4(backdrop.eval(uv - float2(o.x, o.y)));
        acc += float4(backdrop.eval(uv + float2(o.x * 2.0, 0.0)));
        acc += float4(backdrop.eval(uv - float2(o.x * 2.0, 0.0)));
        acc += float4(backdrop.eval(uv + float2(0.0, o.y * 2.0)));
        acc += float4(backdrop.eval(uv - float2(0.0, o.y * 2.0)));
        c = acc / 9.0;
    }

    // 玻璃色调（提亮、去饱和一点，让玻璃在暗背景上也可见）
    c.rgb = mix(c.rgb, uTint, uTintAlpha);

    // 边缘高光：法线朝光方向的部分更亮
    float2 lightDir = normalize(float2(-0.55, -0.85));
    float spec = pow(clamp(dot(n, lightDir), 0.0, 1.0), 2.0);
    float rimBand = smoothstep(uRefractBand, 0.0, abs(d));
    float3 glow = uHighlight * (spec * uHighlightAmt * rimBand);

    // 流光：绕边框顺时针运动，尾部渐隐；经过处额外提亮边缘高光
    if (uProgress >= 0.0) {
        float2 rel = (fragCoord - halfSize) / max(max(halfSize.x, halfSize.y), 1.0);
        float ang = atan(rel.y, rel.x) / (2.0 * PI) + 0.5;
        float flow = fract(uProgress - ang);
        float streak = pow(1.0 - flow, uRimTail);
        glow += uRimColor * (streak * rimBand * 2.2 * uRimAlpha);
    }

    c.rgb += glow;
    return half4(c.rgb, c.a * inside);
}
"""

/** 着色器路径的内部实现，仅在 API 33+ 被加载。 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassShaderRenderer {
    val shader: RuntimeShader = RuntimeShader(GLASS_AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var attachedBitmap: android.graphics.Bitmap? = null

    fun draw(
        canvas: android.graphics.Canvas,
        size: Size,
        backdrop: BackdropSource,
        bounds: Rect,
        radiusPx: Float,
        refractBandPx: Float,
        refractAmountPx: Float,
        innerBlurPx: Float,
        rimProgress: Float?,
        rimAlpha: Float,
    ) {
        if (attachedBitmap !== backdrop.bitmap) {
            shader.setInputShader(
                "backdrop",
                BitmapShader(backdrop.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
            attachedBitmap = backdrop.bitmap
        }

        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uOrigin", backdrop.toBitmapX(bounds.left), backdrop.toBitmapY(bounds.top))
        shader.setFloatUniform("uScale", backdrop.scale, backdrop.scale)
        shader.setFloatUniform("uRadius", radiusPx)
        shader.setFloatUniform("uRefractBand", refractBandPx)
        shader.setFloatUniform("uRefractAmt", refractAmountPx)
        shader.setFloatUniform("uInnerBlur", innerBlurPx)
        shader.setFloatUniform("uTint", GlassTokens.tint[0], GlassTokens.tint[1], GlassTokens.tint[2])
        shader.setFloatUniform("uTintAlpha", GlassTokens.tintAlpha)
        shader.setFloatUniform(
            "uHighlight",
            GlassTokens.highlightColor[0], GlassTokens.highlightColor[1], GlassTokens.highlightColor[2],
        )
        shader.setFloatUniform("uHighlightAmt", GlassTokens.highlightAlpha)
        shader.setFloatUniform("uRimColor", GlassTokens.rimColor[0], GlassTokens.rimColor[1], GlassTokens.rimColor[2])
        shader.setFloatUniform("uRimTail", GlassTokens.rimTail)
        shader.setFloatUniform("uRimAlpha", rimAlpha)
        shader.setFloatUniform("uProgress", rimProgress ?: -1f)

        paint.shader = shader
        canvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}

/**
 * 液态玻璃表面。
 *
 * @param backdrop 背景来源；为 null 时退化为半透明描边（DRM 页面抓帧失败等场景）
 * @param cornerRadius 圆角
 * @param rimProgress 0..1 的流光进度；null 表示不显示流光
 * @param shadow 是否带悬浮投影
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    backdrop: BackdropSource?,
    cornerRadius: Dp = GlassTokens.menuCorner,
    rimProgress: Float? = null,
    rimAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val refractBandPx = with(density) { GlassTokens.refractionBand.toPx() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var shaderBroken by remember { mutableStateOf(false) }

    // 着色器编译失败不崩溃：直接降级为磨砂玻璃，并把原因写进日志便于排查
    val renderer = remember {
        if (!GlassCapability.supportsShader) {
            null
        } else {
            runCatching { GlassShaderRenderer() }.onFailure {
                android.util.Log.e("MyViaGlass", "AGSL 着色器初始化失败，已降级为磨砂玻璃", it)
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .drawBehind {
                val drew = if (renderer != null && !shaderBroken && backdrop != null) {
                    runCatching {
                        drawIntoCanvas { canvas ->
                            renderer.draw(
                                canvas = canvas.nativeCanvas,
                                size = size,
                                backdrop = backdrop,
                                bounds = bounds,
                                radiusPx = radiusPx,
                                refractBandPx = refractBandPx,
                                refractAmountPx = GlassTokens.refractionAmount,
                                innerBlurPx = GlassTokens.innerBlur,
                                rimProgress = rimProgress,
                                rimAlpha = rimAlpha,
                            )
                        }
                    }.onFailure { error ->
                        // 着色器一旦在运行时出错（例如 uniform 名不匹配），
                        // 永久切回磨砂实现，绝不每帧重试或崩溃
                        shaderBroken = true
                        android.util.Log.e("MyViaGlass", "着色器绘制失败，已降级为磨砂玻璃", error)
                    }.isSuccess
                } else {
                    false
                }
                if (!drew) drawFrostedFallback(backdrop, bounds, radiusPx)
                drawGlassEdge(radiusPx, rimProgress, rimAlpha)
            },
        content = content,
    )
}

/** 低版本/无背景时的降级：半透明磨砂 + 描边。 */
private fun DrawScope.drawFrostedFallback(
    backdrop: BackdropSource?,
    bounds: Rect,
    radiusPx: Float,
) {
    val base = Color.White.copy(alpha = if (GlassCapability.supportsBlur) 0.16f else 0.22f)
    if (backdrop != null) {
        val shader = BitmapShader(
            backdrop.bitmap,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(backdrop.scale, backdrop.scale)
                    postTranslate(backdrop.toBitmapX(bounds.left), backdrop.toBitmapY(bounds.top))
                },
            )
        }
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.nativeCanvas.drawRoundRect(
                0f, 0f, size.width, size.height,
                radiusPx, radiusPx, paint,
            )
        }
        drawRoundRect(
            color = base,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx),
        )
    } else {
        drawRoundRect(
            color = base,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx),
        )
    }
}

/** 玻璃边缘：极细描边（上亮下暗，模拟折射边缘），以及流光。 */
private fun DrawScope.drawGlassEdge(radiusPx: Float, rimProgress: Float?, rimAlpha: Float) {
    val corner = androidx.compose.ui.geometry.CornerRadius(radiusPx)
    val border = 0.9f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = GlassTokens.borderAlpha * 1.6f),
                Color.White.copy(alpha = GlassTokens.borderAlpha * 0.4f),
            ),
        ),
        cornerRadius = corner,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = border),
    )
    if (rimProgress != null && rimAlpha > 0.01f) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rimColor = Color(
            GlassTokens.rimColor[0],
            GlassTokens.rimColor[1],
            GlassTokens.rimColor[2],
        )
        val gradient = android.graphics.SweepGradient(
            cx, cy,
            intArrayOf(
                rimColor.copy(alpha = rimAlpha).toArgb(),
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb(),
            ),
            floatArrayOf(0f, 0.22f, 1f),
        )
        gradient.setLocalMatrix(
            Matrix().apply { postRotate(rimProgress * 360f - 90f, cx, cy) },
        )
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = gradient
                style = Paint.Style.STROKE
                strokeWidth = 2.4f
            }
            canvas.nativeCanvas.drawRoundRect(
                0f, 0f, size.width, size.height,
                radiusPx, radiusPx, paint,
            )
        }
    }
}
