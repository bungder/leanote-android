package org.houxg.leamonax.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.elvishew.xlog.XLog;
import com.raizlabs.android.dbflow.StringUtils;
import com.tencent.bugly.crashreport.CrashReport;

import org.houxg.leamonax.BuildConfig;
import org.houxg.leamonax.R;
import org.houxg.leamonax.database.NoteDataStore;
import org.houxg.leamonax.model.Note;
import org.houxg.leamonax.service.NoteService;
import org.houxg.leamonax.ui.edit.EditorFragment;
import org.houxg.leamonax.ui.edit.NoteEditActivity;
import org.houxg.leamonax.utils.DialogDisplayer;
import org.houxg.leamonax.utils.NetworkUtils;
import org.houxg.leamonax.utils.ToastUtils;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

public class NotePreviewActivity extends BaseActivity implements EditorFragment.EditorFragmentListener {

    private static final String TAG = "NotePreviewActivity:";
    public static final String EXT_NOTE_LOCAL_ID = "ext_note_local_id";
    public static final int REQ_EDIT = 1;

    private EditorFragment mEditorFragment;
    private Note mNote;

    @BindView(R.id.rl_action)
    View mActionContainer;
    @BindView(R.id.tv_revert)
    View mRevertBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        ButterKnife.bind(this);
        initToolBar((Toolbar) findViewById(R.id.toolbar), true);
        long noteLocalId = getIntent().getLongExtra(EXT_NOTE_LOCAL_ID, -1);
        mNote = NoteDataStore.getByLocalId(noteLocalId);
        if (mNote == null) {
            ToastUtils.show(this, R.string.note_not_found);
            CrashReport.postCatchedException(new IllegalStateException("Note not found while preview, localId=" + noteLocalId));
            finish();
            return;
        }
        mEditorFragment = EditorFragment.getNewInstance(mNote.isMarkDown(), false);
        getSupportFragmentManager().beginTransaction().add(R.id.container, mEditorFragment).commit();
    }

    public static Intent getOpenIntent(Context context, long noteLocalId) {
        Intent intent = new Intent(context, NotePreviewActivity.class);
        intent.putExtra(EXT_NOTE_LOCAL_ID, noteLocalId);
        return intent;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.preview, menu);
        menu.findItem(R.id.action_print).setVisible(BuildConfig.DEBUG);
        menu.findItem(R.id.action_get).setVisible(BuildConfig.DEBUG);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_rotate:
                if (ORIENTATION_PORTRAIT == getResources().getConfiguration().orientation) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                return true;
            case R.id.action_edit:
                startActivityForResult(NoteEditActivity.getOpenIntent(this, mNote.getId(), false), REQ_EDIT);
                return true;
            case R.id.action_get:
                Observable.create(
                        new Observable.OnSubscribe<Void>() {
                            @Override
                            public void call(Subscriber<? super Void> subscriber) {
                                mEditorFragment.getContent();
                                subscriber.onNext(null);
                                subscriber.onCompleted();
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe();
                return true;
            case R.id.action_print:
                XLog.i(TAG + mNote.getContent());
            case R.id.action_get_url:
//                XLog.i(TAG + "------" + mEditorFragment.getWebviewURL());
                String url = mEditorFragment.getWebviewURL();
                Context context = getApplicationContext();
                int duration = Toast.LENGTH_SHORT;
                String info = "";
                if (StringUtils.isNullOrEmpty(url)) {
                    info = "URL为空";
                } else if (url.startsWith("file")) {
                    info = "目前打开的是本地文件";
                } else {
                    ClipboardManager cmb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cmb.setPrimaryClip(ClipData.newPlainText("", url));
                    info = "URL已经复制到粘贴板";
                }
                Toast toast = Toast.makeText(context, info, duration);
                toast.show();

            case R.id.action_open_with_browser:
                String url2 = mEditorFragment.getWebviewURL();
                if (StringUtils.isNullOrEmpty(url2)) {
                    Toast.makeText(getApplicationContext(), "URL为空", Toast.LENGTH_SHORT).show();
                } else if (url2.startsWith("file")) {
                    Toast.makeText(getApplicationContext(), "目前打开的是本地文件", Toast.LENGTH_SHORT).show();
                }else {
                    Uri uri = Uri.parse(url2);
                    Intent intent = new Intent();
                    intent.setAction("android.intent.action.VIEW");
                    intent.setData(uri);
                    startActivity(intent);
                }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT) {
            switch (resultCode) {
                case RESULT_OK:
                    mNote = NoteDataStore.getByLocalId(mNote.getId());
                    if (mNote == null) {
                        finish();
                    } else {
                        refresh();
                    }
                    break;
                case NoteEditActivity.RESULT_CONFLICT:
                    finish();
                    break;
            }
        }
    }

    @OnClick(R.id.tv_save)
    void push() {
        Observable.create(
                new Observable.OnSubscribe<Long>() {
                    @Override
                    public void call(Subscriber<? super Long> subscriber) {
                        if (!subscriber.isUnsubscribed()) {
                            NetworkUtils.checkNetwork();
                            NoteService.saveNote(mNote.getId());
                            subscriber.onNext(mNote.getId());
                            subscriber.onCompleted();
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        DialogDisplayer.showProgress(NotePreviewActivity.this, R.string.saving_note);
                    }
                })
                .subscribe(new Observer<Long>() {
                    @Override
                    public void onCompleted() {
                        DialogDisplayer.dismissProgress();
                    }

                    @Override
                    public void onError(Throwable e) {
                        DialogDisplayer.dismissProgress();
                        ToastUtils.show(NotePreviewActivity.this, e.getMessage());
                    }

                    @Override
                    public void onNext(Long aLong) {
                        mNote = NoteDataStore.getByLocalId(mNote.getId());
                        mNote.setIsDirty(false);
                        mNote.save();
                        refresh();
                    }
                });
    }

    @OnClick(R.id.tv_revert)
    void revert() {
        if (!NetworkUtils.isNetworkAvailable()) {
            ToastUtils.showNetworkUnavailable(this);
            return;
        }
        Observable.create(
                new Observable.OnSubscribe<Boolean>() {
                    @Override
                    public void call(Subscriber<? super Boolean> subscriber) {
                        if (!subscriber.isUnsubscribed()) {
                            subscriber.onNext(NoteService.revertNote(mNote.getNoteId()));
                            subscriber.onCompleted();
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        DialogDisplayer.showProgress(NotePreviewActivity.this, R.string.reverting);
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        DialogDisplayer.dismissProgress();
                    }
                })
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean isSucceed) {
                        if (isSucceed) {
                            mNote = NoteDataStore.getByServerId(mNote.getNoteId());
                            refresh();
                        }
                    }
                });

    }

    @Override
    public Uri createImage(String filePath) {
        return null;
    }

    @Override
    public Uri createAttach(String filePath) {
        return null;
    }

    @Override
    public void onInitialized() {
        refresh();
    }

    private void refresh() {
        //TODO: animation
        mActionContainer.setVisibility(mNote.isDirty() ? View.VISIBLE : View.GONE);
        mRevertBtn.setVisibility(mNote.getUsn() > 0 ? View.VISIBLE : View.GONE);

        mEditorFragment.setTitle(TextUtils.isEmpty(mNote.getTitle()) ? getString(R.string.untitled) : mNote.getTitle());
        mEditorFragment.setContent(mNote.getContent());
    }
}
