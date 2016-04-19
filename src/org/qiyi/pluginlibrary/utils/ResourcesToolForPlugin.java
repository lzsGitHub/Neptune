package org.qiyi.pluginlibrary.utils;

import java.lang.reflect.Field;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

/**
 * Wrapper class for invoker to get resource id Wrapper of gen/R.java
 */
public class ResourcesToolForPlugin {
    static final String ANIM = "anim";
    static final String ANIMATOR = "animator";
    static final String ARRAY = "array";
    static final String ATTR = "attr";
    static final String BOOL = "bool";
    static final String COLOR = "color";
    static final String DIMEN = "dimen";
    static final String DRAWABLE = "drawable";
    static final String ID = "id";
    static final String INTEGER = "integer";
    static final String INTERPOLATOR = "interpolator";
    static final String LAYOUT = "layout";
    static final String MENU = "menu";
    static final String RAW = "raw";
    static final String STRING = "string";
    static final String STYLE = "style";
    static final String STYLEABLE = "styleable";
    static final String TRANSITION = "transition";
    static final String XML = "xml";

    String mPackageName;
    Resources mResources;
    ClassLoader mClassLoader;

    /**
     * Whether resolve resource by reflect to package.R.java
     */
    private boolean mResolveByReflect = false;

    public ResourcesToolForPlugin(Context appContext) {
        if (null != appContext) {
            mPackageName = appContext.getPackageName();
            mResources = appContext.getResources();
            mClassLoader = appContext.getClassLoader();
        }
    }

    /**
     * Create resource tool
     *
     * @param resource
     * @param packageName
     * @param clsLoader corresponding classloader for resource/view.java, can be
     * null, if null will use Class.forName(xx)
     */
    public ResourcesToolForPlugin(Resources resource, String packageName, ClassLoader clsLoader) {
        if (resource != null && !TextUtils.isEmpty(packageName)) {
            mPackageName = packageName;
            mResources = resource;
            mClassLoader = clsLoader;
        }
    }

    /**
     * Create resource tool
     *
     * @param resource
     * @param packageName
     * @param clsLoader corresponding classloader for resource/view.java, can be
     * null, if null will use Class.forName(xx)
     */
    public ResourcesToolForPlugin(Resources resource, String packageName, ClassLoader clsLoader, boolean resolveByReflect) {
        if (resource != null && !TextUtils.isEmpty(packageName)) {
            mPackageName = packageName;
            mResources = resource;
            mClassLoader = clsLoader;
        }
        mResolveByReflect = resolveByReflect;
    }

    /**
     * Set resolve type for resolve resource id, set true will resolve resource
     * id by reflection to packagename.R.java, otherwise will use
     * Resource.getIdentifier(resName).
     *
     * @param resolveByReflect
     */
    public void setResolveType(boolean resolveByReflect) {
        mResolveByReflect = resolveByReflect;
    }

    /**
     * 获取主包资源id
     *
     * @param sourceName
     * @param sourceType
     * @return
     */
    private int getResourceId(String sourceName, String sourceType) {
        if (mResources == null || TextUtils.isEmpty(sourceName)) {
            return -1;
        } else {
            return mResources.getIdentifier(sourceName, sourceType, mPackageName);
        }

    }

    public int getResourceIdForString(String sourceName) {
        if (TextUtils.isEmpty(sourceName)) {
            sourceName = "emptey_string_res";
        }

        int id = -1;
        if (mResolveByReflect) {
            id = optValue(sourceName, STRING);
        } else {
            id = getResourceId(sourceName, STRING);
        }
        if (id < 0) {
            id = getResourceId("emptey_string_res", STRING);
        }

        return id;
    }

    public int getResourceIdForID(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, ID);
        }
        return getResourceId(sourceName, ID);
    }

    public int getResourceIdForLayout(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, LAYOUT);
        }
        return getResourceId(sourceName, LAYOUT);
    }

    public int getResourceIdForDrawable(String sourceName) {
        if (TextUtils.isEmpty(sourceName)) {
            sourceName = "default_empty_drawable_transparent";// 默认一个透明图片资源
        }
        if (mResolveByReflect) {
            return optValue(sourceName, DRAWABLE);
        }
        return getResourceId(sourceName, DRAWABLE);
    }

    public int getResourceIdForStyle(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, STYLE);
        }
        return getResourceId(sourceName, STYLE);
    }

    public int getResourceIdForColor(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, COLOR);
        }
        return getResourceId(sourceName, COLOR);
    }

    public int getResourceIdForRaw(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, RAW);
        }
        return getResourceId(sourceName, RAW);
    }

    public int getResourceForAnim(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, ANIM);
        }
        return getResourceId(sourceName, ANIM);
    }

    public int getResourceForAnimator(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, ANIMATOR);
        }
        return getResourceId(sourceName, ANIMATOR);
    }

    public int getResourceForAttr(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, ATTR);
        }
        return getResourceId(sourceName, ATTR);
    }

    public int getResourceForArray(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, ARRAY);
        }
        return getResourceId(sourceName, ARRAY);
    }

    public int getResourceForBool(String sourceName) {
        return optValue(sourceName, BOOL);
    }

    public int getResourceForDimen(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, DIMEN);
        }
        return getResourceId(sourceName, DIMEN);
    }

    public int getResourceForInteger(String sourceName) {
        return optValue(sourceName, INTEGER);
    }

    public int getResourceForInterpolator(String sourceName) {
        return optValue(sourceName, INTERPOLATOR);
    }

    public int getResourceForMenu(String sourceName) {
        return optValue(sourceName, MENU);
    }

    public int getResourceForStyleable(String sourceName) {
        return optValue(sourceName, STYLEABLE);
    }

    public int[] getResourceForStyleables(String sourceName) {
        return optValueArray(sourceName, STYLEABLE);
    }

    public int getResourceForTransition(String sourceName) {
        return optValue(sourceName, TRANSITION);
    }

    public int getResourceForXml(String sourceName) {
        if (mResolveByReflect) {
            return optValue(sourceName, XML);
        }
        return getResourceId(sourceName, XML);
    }

    /**
     * Get int id from packagename.R.java result will by int
     *
     * @param resourceName resource name
     * @param resourceType resource type
     * @return
     */
    private int optValue(String resourceName, String resourceType) {
        int result = 0;
        if (TextUtils.isEmpty(resourceName) || TextUtils.isEmpty(resourceType) || TextUtils.isEmpty(mPackageName)) {
            PluginDebugLog.log(ResourcesToolForPlugin.class.getSimpleName(), "optValue resourceName: " + resourceName + " resourceType: "
                    + resourceType + " mPackageName: " + mPackageName + ", just return 0!");
            return result;
        }
        try {
            Class<?> cls;
            if (null != mClassLoader) {
                cls = Class.forName(mPackageName + ".R$" + resourceType, true, mClassLoader);
            } else {
                cls = Class.forName(mPackageName + ".R$" + resourceType);
            }
            Field field = cls.getDeclaredField(resourceName);
            if (null != field) {
                field.setAccessible(true);
                result = field.getInt(cls);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Get int ids from packagename.R.java result will by int[]
     *
     * @param resourceName resource name
     * @param resourceType resource type
     * @return
     */
    private int[] optValueArray(String resourceName, String resourceType) {
        int[] result = null;
        if (TextUtils.isEmpty(resourceName) || TextUtils.isEmpty(resourceType) || TextUtils.isEmpty(mPackageName)) {
            PluginDebugLog.log(ResourcesToolForPlugin.class.getSimpleName(), "optValueArray resourceName: " + resourceName
                    + " resourceType: " + resourceType + " mPackageName: " + mPackageName + ", just return 0!");
            return result;
        }
        try {
            Class<?> cls;
            if (null != mClassLoader) {
                cls = Class.forName(mPackageName + ".R$" + resourceType, true, mClassLoader);
            } else {
                cls = Class.forName(mPackageName + ".R$" + resourceType);
            }
            Field field = cls.getDeclaredField(resourceName);
            if (null != field) {
                field.setAccessible(true);
                Object res = field.get(cls);
                if (res != null && res.getClass().isArray()) {
                    result = (int[]) res;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
