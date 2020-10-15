package org.mark.thingshello.ctrl.comp.bind;

import android.util.Log;

public class ExclusiveBind {
    Bindable uniqueBindable;

    public void activeBindable(Bindable bindable) {
        if (bindable == null) {
            throw new IllegalArgumentException("param Bindable is null");
        }

        if (bindable != uniqueBindable) {
            synchronized (this) {
                if (uniqueBindable != null) {
                    Log.d("ExclusiveBind", "onUnBind:" + uniqueBindable.getClass().getSimpleName());
                    uniqueBindable.onUnBind();
                }
                uniqueBindable = bindable;
                Log.d("ExclusiveBind", "onBind:" + uniqueBindable.getClass().getSimpleName());
                uniqueBindable.onBind();
            }
        }
    }

    public void release() {
        Log.d("ExclusiveBind", "release");
        uniqueBindable = null;
    }
}
