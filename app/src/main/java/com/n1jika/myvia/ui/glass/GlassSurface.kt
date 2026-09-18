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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃着色器（AGSL，Android 13+）。
 *
 * 完全按《液态玻璃原理及其高性能实现》(forum.cocos.org/raw/171941) 的公式实现：
 *
 * 1. **位移向量场**：光线越靠近玻璃边缘折射越强、到中心正交穿过不再折射，
 *    因此位移矢量长度从边缘的 1 线性衰减到中心的 0（原帖用 [0,1] 归一化的 RG 贴图存储，
 *    这里用圆角矩形 SDF 解析式直接算出同一个场，省掉一张贴图）。
 * 2. **折射**：`采样UV = 基准UV + 位移矢量 * strengthUV`，方向指向玻璃中心，
 *    于是边缘处的背景被"压向"边界，形成透镜般的压缩带。
 * 3. **色散**：R 通道与 B 通道沿位移方向左右各偏移 dispersionStrength，G 通道不动，
 *    边缘因此出现细彩边。
 * 4. **轮廓光**：位移矢量长度超过阈值才属于轮廓；亮心位置由位移矢量的 y 分量决定
 *    （左半亮心 0.75、右半亮心 0.25），最后 `mix(颜色, 白, opacity)` 混进白色。
 *    **全程没有一次加法**，所以玻璃不会自发光、不会泛光。
 *
 * 总采样 3 次（R/G/B），比原帖的 4 次更省。
 */
private const val GLASS_AGSL = """
uniform shader backdrop;      // 背景（已降采样并模糊）
uniform float2 uSize;         // 玻璃尺寸（px）
uniform float2 uOrigin;       // 玻璃左上角在背景位图中的坐标（px）
uniform float2 uScale;        // 背景位图像素 / 屏幕像素
uniform float  uRadius;       // 圆角半径（px）
uniform float  uBand;         // 折射带宽度（px）
uniform float  uStrength;     // 位移强度 = strengthUV（屏幕 px）
uniform float  uDispersion;   // 色散强度
uniform float  uFrost;        // 玻璃霜化程度（0=全透，1=纯白）
uniform float  uHighlightStrength;  // 轮廓阈值
uniform float  uHighlightOpacity;   // 轮廓光最大不透明度
uniform float  uLeftCenter;   // 左半轮廓光亮心
uniform float  uRightCenter;  // 右半轮廓光亮心
uniform float  uSpan;         // 轮廓光渐变跨度
uniform float  uProgress;     // 流光进度（负值=不显示）
uniform float  uRimAlpha;     // 流光强度
uniform float  uRimTail;      // 流光尾部锐度

const float PI = 3.14159265359;

// 圆角矩形有符号距离场：内部为负，边界 0，外部为正
float sdRoundRect(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - radius;
}

half4 main(float2 fragCoord) {
    float2 halfSize = uSize * 0.5;
    float radius = min(uRadius, min(halfSize.x, halfSize.y));
    float2 p = fragCoord - halfSize;
    float d = sdRoundRect(p, halfSize, radius);

    float inside = 1.0 - smoothstep(-1.0, 1.0, d);
    if (inside <= 0.002) {
        return half4(0.0);
    }

    // 外法线
    float2 n = float2(
        sdRoundRect(p + float2(1.0, 0.0), halfSize, radius) - sdRoundRect(p - float2(1.0, 0.0), halfSize, radius),
        sdRoundRect(p + float2(0.0, 1.0), halfSize, radius) - sdRoundRect(p - float2(0.0, 1.0), halfSize, radius)
    );
    n = normalize(n + float2(1e-6, 1e-6));

    // ---- 位移向量场：中心归零、边缘最大 ----
    float m = clamp(1.0 + d / max(uBand, 1.0), 0.0, 1.0);
    // 负号：采样点朝玻璃中心方向偏移（等价于原帖对 Y 取负、让位移指向中心）
    float2 offsetNormal = -n * m;
    float2 offsetUV = offsetNormal * uStrength * uScale;   // 换算成背景位图的像素

    // ---- 折射 + 色散：R/G/B 三次采样 ----
    float2 baseUV = uOrigin + fragCoord * uScale;
    half4 gC = backdrop.eval(baseUV);
    half4 rC = backdrop.eval(baseUV + offsetUV * uDispersion);
    half4 bC = backdrop.eval(baseUV - offsetUV * uDispersion);
    float4 c = float4(rC.r, gC.g, bC.b, 1.0);

    // ---- 玻璃本体：只做霜化混合，不做加法 ----
    c.rgb = mix(c.rgb, float3(1.0), uFrost);

    // ---- 轮廓光：位移矢量长度定位轮廓，y 分量定位亮心 ----
    float len = length(offsetNormal);
    float highlight = 0.0;
    if (len > 1.0 - uHighlightStrength) {
        float g = offsetNormal.y * 0.5 + 0.5;
        float center = offsetNormal.x > 0.0 ? uLeftCenter : uRightCenter;
        highlight = clamp(1.0 - abs(g - center) / uSpan, 0.0, 1.0);
    }

    // ---- 流光：沿同一条轮廓带绕行，中性白 ----
    if (uProgress >= 0.0 && uRimAlpha > 0.0) {
        float2 rel = (fragCoord - halfSize) / max(max(halfSize.x, halfSize.y), 1.0);
        float ang = atan(rel.y, rel.x) / (2.0 * PI) + 0.5;
        float flow = fract(uProgress - ang);
        float streak = pow(1.0 - flow, uRimTail);
        float gate = step(1.0 - uHighlightStrength * 1.5, len);
        highlight = max(highlight, streak * gate * uRimAlpha);
    }

    c.rgb = mix(c.rgb, float3(1.0), clamp(highlight * uHighlightOpacity, 0.0, 1.0));
    return half4(c.rgb, inside);
}
"""

/** 着色器路径的实现，仅在 API 33+ 加载。 */
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
        frost: Float,
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

        val shortSide = minOf(size.width, size.height)
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uOrigin", backdrop.toBitmapX(bounds.left), backdrop.toBitmapY(bounds.top))
        shader.setFloatUniform("uScale", backdrop.scale, backdrop.scale)
        shader.setFloatUniform("uRadius", radiusPx)
        shader.setFloatUniform("uBand", shortSide * GlassTokens.refractionBandFraction)
        shader.setFloatUniform("uStrength", shortSide * GlassTokens.strengthUV)
        shader.setFloatUniform("uDispersion", GlassTokens.dispersionStrength)
        shader.setFloatUniform("uFrost", frost)
        shader.setFloatUniform("uHighlightStrength", GlassTokens.highlightStrength)
        shader.setFloatUniform("uHighlightOpacity", GlassTokens.highlightOpacity)
        shader.setFloatUniform("uLeftCenter", GlassTokens.highlightLeftCenter)
        shader.setFloatUniform("uRightCenter", GlassTokens.highlightRightCenter)
        shader.setFloatUniform("uSpan", GlassTokens.highlightSpan)
        shader.setFloatUniform("uProgress", rimProgress ?: -1f)
        shader.setFloatUniform("uRimAlpha", rimAlpha)
        shader.setFloatUniform("uRimTail", GlassTokens.rimTail)

        paint.shader = shader
        canvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}

/**
 * 液态玻璃表面。
 *
 * @param backdrop 背景来源；为 null 时退化为霜化描边玻璃
 * @param cornerRadius 圆角
 * @param rimProgress 0..1 的流光进度；null 表示不显示
 * @param frost 霜化程度；传负值表示按背景明度自动决定（亮背景薄、暗背景厚，保证黑字可读）
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    backdrop: BackdropSource?,
    cornerRadius: Dp = GlassTokens.menuCorner,
    rimProgress: Float? = null,
    rimAlpha: Float = 1f,
    frost: Float = -1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var shaderBroken by remember { mutableStateOf(false) }

    val effectiveFrost = if (frost >= 0f) frost
    else GlassTokens.frostFor(backdrop?.contentLuminance ?: 1f)

    // 着色器失败不崩溃：降级为霜化玻璃并记录原因
    val renderer = remember {
        if (!GlassCapability.supportsShader) {
            null
        } else {
            runCatching { GlassShaderRenderer() }.onFailure {
                android.util.Log.e("MyViaGlass", "AGSL 着色器初始化失败，已降级", it)
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
                                frost = effectiveFrost,
                                rimProgress = rimProgress,
                                rimAlpha = rimAlpha,
                            )
                        }
                    }.onFailure { error ->
                        shaderBroken = true
                        android.util.Log.e("MyViaGlass", "着色器绘制失败，已降级为霜化玻璃", error)
                    }.isSuccess
                } else {
                    false
                }
                if (!drew) drawFrostedFallback(backdrop, bounds, radiusPx, effectiveFrost)
                drawGlassEdge(radiusPx)
            },
        content = content,
    )
}

/** 无着色器时的降级：背景 + 霜化，同样只做混合、不做加法。 */
private fun DrawScope.drawFrostedFallback(
    backdrop: BackdropSource?,
    bounds: Rect,
    radiusPx: Float,
    frost: Float,
) {
    val corner = CornerRadius(radiusPx)
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
    }
    drawRoundRect(color = Color.White.copy(alpha = frost), cornerRadius = corner)
}

/** 边缘：单层 1px 描边，上亮下弱（方向性），不带任何泛光。 */
private fun DrawScope.drawGlassEdge(radiusPx: Float) {
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = GlassTokens.borderTopAlpha),
                Color.White.copy(alpha = GlassTokens.borderBottomAlpha),
            ),
        ),
        cornerRadius = CornerRadius(radiusPx),
        style = Stroke(width = 1f),
    )
}
