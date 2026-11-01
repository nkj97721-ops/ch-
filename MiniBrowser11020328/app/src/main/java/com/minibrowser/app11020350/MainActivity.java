package com.minibrowser.app11020350;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final int black=Color.BLACK, panel=Color.rgb(16,16,16), white=Color.WHITE, gray=Color.rgb(155,155,155);
    private final ArrayList<WebView> tabs=new ArrayList<>();
    private int current=0;
    private LinearLayout root,bottom;
    private EditText address;
    private WebView web;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(black);
        getWindow().setNavigationBarColor(black);
        CookieManager.getInstance().setAcceptCookie(true);
        buildUi();
        addTab("https://www.google.com");
    }

    private TextView button(String label) {
        TextView v=new TextView(this);
        v.setText(label); v.setTextColor(white); v.setTextSize(15); v.setGravity(Gravity.CENTER);
        v.setPadding(10,10,10,10); v.setBackgroundColor(panel);
        return v;
    }

    private void buildUi() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(black); int inset=(int)(16*getResources().getDisplayMetrics().density+0.5f); root.setPadding(0,inset,0,inset);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(8,8,8,8);
        address=new EditText(this); address.setSingleLine(true); address.setTextColor(white); address.setHintTextColor(gray);
        address.setHint("搜索或输入网址"); address.setTextSize(15); address.setPadding(14,6,14,6);
        address.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gray));
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        bar.addView(address,new LinearLayout.LayoutParams(0,48,1));
        TextView go=button("打开"); bar.addView(go);
        go.setOnClickListener(v->navigate(address.getText().toString()));
        address.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_GO || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                navigate(address.getText().toString()); return true;
            } return false;
        });
        root.addView(bar);
        bottom=new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setBackgroundColor(black);
        addBottom("‹",()->{if(web.canGoBack())web.goBack();});
        addBottom("›",()->{if(web.canGoForward())web.goForward();});
        addBottom("↻",()->web.reload());
        addBottom("＋",()->addTab("https://www.google.com"));
        addBottom("标签",this::showTabs);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,52));
        setContentView(root);
    }

    private void addBottom(String label,Runnable action) {
        TextView b=button(label); b.setTextSize(18);
        bottom.addView(b,new LinearLayout.LayoutParams(0,-1,1));
        b.setOnClickListener(v->action.run());
    }

    private WebView makeWebView() {
        WebView w=new WebView(this); w.setBackgroundColor(black);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        w.getSettings().setLoadsImagesAutomatically(true);
        w.getSettings().setSupportMultipleWindows(false);
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri u=request.getUrl(); String scheme=u.getScheme();
                if(scheme!=null&&!scheme.equals("http")&&!scheme.equals("https")){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
                    return true;
                } return false;
            }
            @Override public void onPageFinished(WebView view,String url){
                if(view==web)address.setText(url);
            }
        });
        w.setOnLongClickListener(v->{
            WebView.HitTestResult hit=w.getHitTestResult();
            if(hit!=null&&hit.getType()==WebView.HitTestResult.SRC_ANCHOR_TYPE&&hit.getExtra()!=null){
                String link=hit.getExtra();
                new AlertDialog.Builder(this).setItems(new String[]{"在新标签页打开","复制链接"},(d,which)->{
                    if(which==0)addTab(link);
                    else{
                        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("URL",link));
                    }
                }).show(); return true;
            } return false;
        });
        return w;
    }

    private void addTab(String url) {
        if(web!=null) web.setVisibility(View.GONE);
        WebView w=makeWebView(); tabs.add(w); current=tabs.size()-1; web=w;
        root.addView(w,1,new LinearLayout.LayoutParams(-1,0,1));
        navigate(url);
    }

    private void switchTab(int index) {
        if(index<0||index>=tabs.size())return;
        if(web!=null)web.setVisibility(View.GONE);
        current=index; web=tabs.get(index); web.setVisibility(View.VISIBLE);
        address.setText(web.getUrl()==null?"":web.getUrl());
    }

    private void showTabs() {
        ArrayList<String> labels=new ArrayList<>();
        for(int i=0;i<tabs.size();i++){
            String u=tabs.get(i).getUrl();
            labels.add((i==current?"● ":"")+(u==null?"新标签页":u));
        }
        labels.add("＋ 新建标签页");
        new AlertDialog.Builder(this).setTitle("标签页").setItems(labels.toArray(new String[0]),(d,which)->{
            if(which==tabs.size())addTab("https://www.google.com");else switchTab(which);
        }).setNegativeButton("关闭",null).show();
    }

    private void navigate(String input) {
        if(input==null)return; String s=input.trim(); if(s.isEmpty())return;
        String url;
        if(s.matches("(?i)^[a-z][a-z0-9+.-]*:.*"))url=s;
        else if(s.contains(" ")||!s.contains(".")){
            try{url="https://www.google.com/search?q="+URLEncoder.encode(s,"UTF-8");}
            catch(Exception e){url="https://www.google.com";}
        } else url="https://"+s;
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
        web.loadUrl(url); web.requestFocus();
    }

    @Override public void onBackPressed() {
        if(web!=null&&web.canGoBack())web.goBack();
        else if(tabs.size()>1){
            WebView old=tabs.remove(current); root.removeView(old); old.destroy();
            current=Math.max(0,current-1); web=null; switchTab(current);
        } else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        for(WebView w:tabs){try{w.stopLoading();w.destroy();}catch(Exception ignored){}}
        tabs.clear(); super.onDestroy();
    }
}
