package clwang.chunyu.me.wcl_droid_plugin_demo.start;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.morgoo.droidplugin.pm.PluginManager;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import clwang.chunyu.me.wcl_droid_plugin_demo.ApkItem;
import clwang.chunyu.me.wcl_droid_plugin_demo.R;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * 启动Apk页面
 * <p>
 * Created by wangchenlong on 16/1/13.
 */
public class StartFragment extends Fragment {

    @Bind(R.id.list_rv_recycler) RecyclerView mRvRecycler;

    private StartAdapter mStartAdapter; // 适配器
    private InstallApkReceiver mInstallApkReceiver; // Apk安装接收器

    // 服务连接
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            loadApks();
        }

        @Override public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Nullable @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LinearLayoutManager llm = new LinearLayoutManager(view.getContext());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        mRvRecycler.setLayoutManager(llm);

        mStartAdapter = new StartAdapter(getActivity());
        mRvRecycler.setAdapter(mStartAdapter);

        mInstallApkReceiver = new InstallApkReceiver();

        mInstallApkReceiver.registerReceiver(this.getActivity());

        if (PluginManager.getInstance().isConnected()) {
            loadApks();
        } else {
            PluginManager.getInstance().addServiceConnection(mServiceConnection);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
        mInstallApkReceiver.unregisterReceiver(this.getActivity());
    }

    // 加载Apk
    private void loadApks() {
        // 异步加载, 防止Apk过多, 影响速度
        Observable.just(getApkFromInstall())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mStartAdapter::setApkItems);
    }

    // 获取安装中获取Apk
    private ArrayList<ApkItem> getApkFromInstall() {
        ArrayList<ApkItem> apkItems = new ArrayList<>();
        try {
            final List<PackageInfo> infos = PluginManager.getInstance().getInstalledPackages(0);
            if (infos == null) {
                return apkItems;
            }
            final PackageManager pm = getActivity().getPackageManager();
            for (final PackageInfo info : infos) {
                apkItems.add(new ApkItem(pm, info, info.applicationInfo.publicSourceDir));
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return apkItems;
    }

    private class InstallApkReceiver extends BroadcastReceiver {

        public void registerReceiver(Context context) {
            IntentFilter f = new IntentFilter();
            f.addAction(PluginManager.ACTION_PACKAGE_ADDED);
            f.addAction(PluginManager.ACTION_PACKAGE_REMOVED);
            f.addDataScheme("package");
            context.registerReceiver(this, f);
        }

        public void unregisterReceiver(Context context) {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (PluginManager.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                try {
                    PackageManager pm = getActivity().getPackageManager();
                    String pkg = intent.getData().getAuthority();
                    PackageInfo info = PluginManager.getInstance().getPackageInfo(pkg, 0);
                    mStartAdapter.addApkItem(new ApkItem(pm, info, info.applicationInfo.publicSourceDir));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (PluginManager.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                String pkg = intent.getData().getAuthority();
                int N = mStartAdapter.getCount();
                ApkItem iremovedItem = null;
                for (int i = 0; i < N; i++) {
                    ApkItem item = mStartAdapter.getApkItem(i);
                    if (TextUtils.equals(item.packageInfo.packageName, pkg)) {
                        iremovedItem = item;
                        break;
                    }
                }
                if (iremovedItem != null) {
                    mStartAdapter.removeApkItem(iremovedItem);
                }
            }
        }
    }
}