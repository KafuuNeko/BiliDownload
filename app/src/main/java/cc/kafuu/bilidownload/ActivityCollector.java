package cc.kafuu.bilidownload;

import android.app.Activity;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ActivityCollector {
    private static final String TAG = "ActivityCollector";

    private static final List<Activity> activities = new ArrayList<>();

    public static boolean contains(Activity activity) {
        return activities.contains(activity);
    }

    public static <T> boolean contains(Class<T> activityClass) {
        Log.d(TAG, "contains: " + activityClass.getName());
        for (Activity activity : activities) {
            //Log.d(TAG, "contains: " + activity.getClass().getName());
            if (activity.getClass().getName().equals(activityClass.getName())) {
                return true;
            }
        }
        return false;
    }

    public static void addActivity(Activity activity) {
        activities.add(activity);
    }

    public static void removeActivity(Activity activity) {
        activities.remove(activity);
    }

    public static void finishAll() {
        Log.d(TAG, "finishAll");
        for (Activity activity : activities) {
            if (!activity.isFinishing()) {
                activity.finish();
            }
        }
        activities.clear();
    }
}
