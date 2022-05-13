package cc.kafuu.bilidownload.adapter;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.List;


public class PersonalFragmentPagesAdapter extends FragmentPagerAdapter {
    private final List<Pair<CharSequence, Fragment>> mFragments;

    public PersonalFragmentPagesAdapter(List<Pair<CharSequence, Fragment>> fragments, @NonNull FragmentManager fm, int behavior) {
        super(fm, behavior);

        mFragments = fragments;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return mFragments.get(position).second;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return mFragments.get(position).first;
    }

    @Override
    public int getCount() {
        return mFragments.size();
    }
}
