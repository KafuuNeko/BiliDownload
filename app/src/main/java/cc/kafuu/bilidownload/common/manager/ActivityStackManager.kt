package cc.kafuu.bilidownload.common.manager

import android.app.Activity
import java.util.Stack

object ActivityStackManager {
    private val mActivityStack: Stack<Activity> = Stack()

    fun pushActivity(activity: Activity) {
        mActivityStack.push(activity)
    }

    fun popActivity() {
        mActivityStack.pop()
    }

    fun removeActivity(activity: Activity) {
        mActivityStack.remove(activity)
    }
}