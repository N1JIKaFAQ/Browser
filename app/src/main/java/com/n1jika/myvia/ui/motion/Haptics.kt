package com.n1jika.myvia.ui.motion

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 轻触感反馈。苹果式的"点一下有回应"靠的就是这个 + 动画同步。
 */
object Haptics {

    /** 极轻的"嗒"：按钮按下、菜单键弹动 */
    fun tick(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 稍强的确认：搜索提交、两个元素碰撞到位 */
    fun confirm(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
}

/** 在 Composable 里拿一个稳定的触感入口。 */
@Composable
fun rememberHaptics(): (Kind) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { kind ->
            when (kind) {
                Kind.Tick -> Haptics.tick(view)
                Kind.Confirm -> Haptics.confirm(view)
            }
        }
    }
}

enum class Kind { Tick, Confirm }
