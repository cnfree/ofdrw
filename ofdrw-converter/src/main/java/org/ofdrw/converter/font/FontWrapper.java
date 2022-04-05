package org.ofdrw.converter.font;

/**
 * 标识字体是否是被相近字体替换
 * @author yuanfangme
 * @since 2021-06-04 17:10:18
 */
public class FontWrapper<T> {

    T font;

    private boolean enableLoad;

    public FontWrapper() {
    }

    public FontWrapper(T font, boolean enableLoad) {
        this.font = font;
        this.enableLoad = enableLoad;
    }

    public T getFont() {
        return font;
    }

    public void setFont(T font) {
        this.font = font;
    }

    public boolean isEnableLoad() {
        return enableLoad;
    }

    public void setEnableLoad(boolean enableLoad) {
        this.enableLoad = enableLoad;
    }
}
