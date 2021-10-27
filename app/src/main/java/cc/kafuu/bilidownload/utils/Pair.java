package cc.kafuu.bilidownload.utils;

public class Pair<F extends Object, S extends Object> {
    public F first;
    public S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }
}
