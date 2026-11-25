package app.filereader;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.content.pm.ActivityInfo;
import android.provider.OpenableColumns;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;
import android.util.Base64;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import org.json.JSONArray;
import android.widget.Toast;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.PowerManager;
import android.os.SystemClock;
import android.content.ClipData;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class MainActivity extends Activity {
static final String SAVE_JS="(function(){if(window.__saveHook)return;window.__saveHook=1;document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;if(!a||!a.href||a.href.indexOf('blob:')!==0)return;e.preventDefault();e.stopPropagation();var nm=a.getAttribute('download')||'file';fetch(a.href).then(function(r){return r.blob()}).then(function(b){var fr=new FileReader();fr.onload=function(){var d=String(fr.result);AndroidSave.save(nm,b.type||'',d.substring(d.indexOf(',')+1))};fr.readAsDataURL(b)})},true)})();";
 static final String TTS_JS="(function(){ if(window.__ttsPoly||!window.AndroidTTS)return; window.__ttsPoly=1; var map={},n=0,vs=[],ss={}; function load(){try{vs=JSON.parse(AndroidTTS.voices()).map(function(v){return{name:v.name,voiceURI:v.name,lang:v.lang,localService:!!v.local,default:false}})}catch(e){vs=[]}} function U(t){this.text=t===undefined?'':String(t);this.lang='';this.voice=null;this.rate=1;this.pitch=1;this.volume=1;this.onend=null;this.onerror=null;this.onstart=null;this.onboundary=null} ss.speaking=false;ss.pending=false;ss.paused=false;ss.onvoiceschanged=null; ss.getVoices=function(){if(!vs.length)load();return vs}; ss.speak=function(u){ var id=++n;map[id]=u;ss.speaking=true; var v=(u.voice&&u.voice.name)||''; AndroidTTS.speak(id,u.text,u.lang||(u.voice&&u.voice.lang)||'',v,+u.rate||1,+u.pitch||1,u.volume==null?1:+u.volume); try{u.onstart&&u.onstart({})}catch(e){} }; ss.cancel=function(){map={};ss.speaking=false;try{AndroidTTS.cancel()}catch(e){}}; ss.pause=function(){};ss.resume=function(){}; window.__ttsEv=function(id,t){ var u=map[id];if(!u)return;delete map[id]; ss.speaking=Object.keys(map).length>0; try{if(t==='end'){u.onend&&u.onend({})}else{u.onerror&&u.onerror({error:'synthesis-failed'})}}catch(e){} }; window.__ttsVoicesChanged=function(){load();try{ss.onvoiceschanged&&ss.onvoiceschanged({})}catch(e){}}; try{window.SpeechSynthesisUtterance=U}catch(e){try{Object.defineProperty(window,'SpeechSynthesisUtterance',{value:U,configurable:true,writable:true})}catch(e2){}} try{window.speechSynthesis=ss}catch(e){} if(window.speechSynthesis!==ss){try{Object.defineProperty(window,'speechSynthesis',{value:ss,configurable:true,writable:true})}catch(e3){}} load(); })();";
 static final String SINGLE_JS="(function(){if(window.__single||!window.HTMLMediaElement)return;window.__single=1;var P=HTMLMediaElement.prototype,op=P.play,T=[];P.play=function(){var s=this;if(!s.loop&&!s.muted){if(T.indexOf(s)<0)T.push(s);for(var i=0;i<T.length;i++){var e=T[i];if(e!==s&&!e.paused&&!e.loop&&!e.muted){try{e.pause()}catch(x){}}}}return op.apply(this,arguments)}})();";
 static final String HOST="appassets.androidplatform.net";
 WebView web; boolean ready=false; int seq=0; ValueCallback<Uri[]> chooser; byte[] saveData; View customView; WebChromeClient.CustomViewCallback customCb; FrameLayout fsHolder; static MainActivity cur; boolean visible=false,askedNotif=false; OutputStream shareOut; File shareFile; String shareMime="*/*"; OutputStream saveOut; Uri saveUri; String saveName="",saveMime=""; List<String> dlPieces=new ArrayList<>(); List<File> dlFiles=new ArrayList<>(); int dlIdx=0,dlTok=0,dlFails=0,dlErr=0; AacEnc aac=null; boolean aacTried=false; File dlOut=null; ExecutorService ioExec=Executors.newSingleThreadExecutor(); java.util.Set<String> ttsPrompted=new java.util.HashSet<>(); boolean dlBusy=false; String dlName="audio"; boolean ext=false; TextToSpeech tts; boolean ttsReady=false; Runnable ttsPending; String ttsJs=TTS_JS;
 final List<String[]> pending=new ArrayList<>(); final Map<String,Uri> opened=new HashMap<>();

 @Override protected void onCreate(Bundle b){
  cur=this;
  super.onCreate(b);
  WebViewAssetLoader loader=new WebViewAssetLoader.Builder().setDomain(HOST)
   .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this))
   .addPathHandler("/opened/",path->{
     Uri u=opened.get(path); if(u==null) return null;
     try{InputStream in=getContentResolver().openInputStream(u);String t=getContentResolver().getType(u);
      return new WebResourceResponse(t==null?"application/octet-stream":t,null,in);}catch(Exception e){return null;}})
   .build();
  web=new WebView(this); setContentView(web);
  WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
  s.setMediaPlaybackRequiresUserGesture(false); s.setAllowFileAccess(false); s.setBlockNetworkLoads(true);
  web.addJavascriptInterface(new Object(){
   @JavascriptInterface public boolean shareBegin(String name,String mime){
    try{
     File dir=new File(getCacheDir(),"share"); dir.mkdirs();
     File[] old=dir.listFiles(); if(old!=null)for(File f:old)f.delete();
     String n=(name==null||name.isEmpty())?"file":name.replace('/','_');
     shareFile=new File(dir,n); shareOut=new FileOutputStream(shareFile);
     shareMime=(mime==null||mime.isEmpty())?"*/*":mime;
     return true;
    }catch(Exception ex){return false;}
   }
   @JavascriptInterface public boolean shareChunk(String b64){
    try{shareOut.write(Base64.decode(b64,Base64.DEFAULT));return true;}catch(Exception ex){return false;}
   }
   @JavascriptInterface public boolean shareEnd(){
    try{
     shareOut.flush(); shareOut.close(); shareOut=null;
     final Uri u=Uri.parse("content://app.filereader.share/"+Uri.encode(shareFile.getName()));
     final String mm=shareMime, nm=shareFile.getName();
     runOnUiThread(()->{
      try{
       Intent i=new Intent(Intent.ACTION_SEND); i.setType(mm); i.putExtra(Intent.EXTRA_STREAM,u);
       i.setClipData(ClipData.newRawUri(nm,u)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
       startActivity(Intent.createChooser(i,null));
      }catch(Exception ex){}
     });
     return true;
    }catch(Exception ex){return false;}
   }
   @JavascriptInterface public boolean begin(String name,String mime){
    try{
     if(Build.VERSION.SDK_INT<29)return false;
     abortSave();
     ContentValues cv=new ContentValues();
     cv.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
     cv.put(MediaStore.MediaColumns.MIME_TYPE,(mime==null||mime.isEmpty())?"application/octet-stream":mime);
     cv.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download");
     cv.put(MediaStore.MediaColumns.IS_PENDING,1);
     Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
     if(u==null)return false;
     saveUri=u; saveName=name; saveMime=mime;
     saveOut=getContentResolver().openOutputStream(u);
     if(saveOut==null){abortSave();return false;}
     return true;
    }catch(Exception ex){abortSave();return false;}
   }
   @JavascriptInterface public boolean chunk(String b64){
    try{saveOut.write(Base64.decode(b64,Base64.DEFAULT));return true;}catch(Exception ex){abortSave();return false;}
   }
   @JavascriptInterface public String end(){
    try{
     saveOut.flush(); saveOut.close(); saveOut=null;
     ContentValues cv=new ContentValues(); cv.put(MediaStore.MediaColumns.IS_PENDING,0);
     getContentResolver().update(saveUri,cv,null,null);
     String shown=saveName;
     try(Cursor c=getContentResolver().query(saveUri,new String[]{MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())shown=c.getString(0);}catch(Exception ex2){}
     JSONObject o=new JSONObject(); o.put("ok",true); o.put("uri",saveUri.toString()); o.put("name",shown);
     saveUri=null;
     return o.toString();
    }catch(Exception ex){abortSave();return "{\"ok\":false}";}
   }
   @JavascriptInterface public void open(String uri,String mime){
    runOnUiThread(()->{
     try{
      Intent i=new Intent(Intent.ACTION_VIEW); i.setDataAndType(Uri.parse(uri),(mime==null||mime.isEmpty())?"*/*":mime);
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
     }catch(Exception ex){Toast.makeText(MainActivity.this,"\u0644\u0627 \u064a\u0648\u062c\u062f \u062a\u0637\u0628\u064a\u0642 \u0644\u0641\u062a\u062d \u0647\u0630\u0627 \u0627\u0644\u0645\u0644\u0641",Toast.LENGTH_LONG).show();}
    });
   }
   @JavascriptInterface public void share(String uri,String mime){
    runOnUiThread(()->{
     try{
      Intent i=new Intent(Intent.ACTION_SEND); i.setType((mime==null||mime.isEmpty())?"*/*":mime);
      i.putExtra(Intent.EXTRA_STREAM,Uri.parse(uri)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(i,null));
     }catch(Exception ex){}
    });
   }
   @JavascriptInterface public void save(String name,String mime,String b64){
    try{saveData=Base64.decode(b64,Base64.DEFAULT);}catch(Exception e){return;}
    final String n=(name==null||name.isEmpty())?"file":name; final String m=(mime==null||mime.isEmpty())?"application/octet-stream":mime;
    runOnUiThread(()->{
     Intent c=new Intent(Intent.ACTION_CREATE_DOCUMENT); c.addCategory(Intent.CATEGORY_OPENABLE); c.setType(m); c.putExtra(Intent.EXTRA_TITLE,n);
     try{startActivityForResult(c,2);}catch(Exception e){saveData=null;}
    });
   }
  },"AndroidSave");
  tts=new TextToSpeech(this,st->{
   if(st!=TextToSpeech.SUCCESS)return;
   tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
    @Override public void onStart(String id){}
    @Override public void onDone(String id){ev(id,"end");}
    @Override @SuppressWarnings("deprecation") public void onError(String id){ev(id,"error");}
    @Override public void onError(String id,int code){dlErr=code;ev(id,"error");}
   });
   ttsReady=true;
   runOnUiThread(()->{
    if(ttsPending!=null){Runnable r=ttsPending;ttsPending=null;r.run();}
    web.evaluateJavascript("window.__ttsVoicesChanged&&window.__ttsVoicesChanged()",null);
   });
  });
  web.addJavascriptInterface(new Object(){
   @JavascriptInterface public String voices(){
    JSONArray a=new JSONArray();
    try{ if(ttsReady&&tts.getVoices()!=null) for(Voice v:tts.getVoices()){JSONObject o=new JSONObject();o.put("name",v.getName());o.put("lang",v.getLocale().toLanguageTag());o.put("local",!v.isNetworkConnectionRequired());a.put(o);} }catch(Exception ex){}
    return a.toString();
   }
   @JavascriptInterface public void speak(int id,String text,String lang,String voice,float rate,float pitch,float vol){
    final String t=text==null?"":text;
    runOnUiThread(()->{
     Runnable r=()->doSpeak(id,t,lang,voice,rate,pitch,vol);
     if(ttsReady)r.run(); else ttsPending=r;
    });
   }
   @JavascriptInterface public void synthesize(String text,String lang,String voice,float rate,float pitch,String name){
    final String t=text==null?"":text;
    runOnUiThread(()->startDl(t,lang,voice,rate,pitch,name));
   }
   @JavascriptInterface public void cancel(){ttsPending=null;runOnUiThread(()->{dlAbort();if(tts!=null&&!dlBusy)tts.stop();});}
  },"AndroidTTS");
  web.addJavascriptInterface(new Object(){
   @JavascriptInterface public void landscape(boolean on){
    runOnUiThread(()->{try{setRequestedOrientation(on?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}catch(Exception ex){}});
   }
  },"AndroidUi");
  web.addJavascriptInterface(new Object(){
   @JavascriptInterface public void update(String title,String sub,boolean playing,double posMs,double durMs){
    final String t=title==null?"":title, sb=sub==null?"":sub; final long p=(long)posMs, d=(long)durMs;
    runOnUiThread(()->{
     if(playing&&Build.VERSION.SDK_INT>=33&&!askedNotif&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){askedNotif=true;try{requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},77);}catch(Exception ex){}}
     MediaService.push(MainActivity.this,t,sb,playing,p,d);
    });
   }
   @JavascriptInterface public void stop(){runOnUiThread(()->{MediaService.shutdown();if(!visible&&ext)finishAndRemoveTask();});}
  },"AndroidMedia");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return loader.shouldInterceptRequest(r.getUrl());}
   @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap bm){super.onPageStarted(v,u,bm);v.evaluateJavascript(ttsJs,null);}
   @Override public void onPageFinished(WebView v,String u){v.evaluateJavascript(SAVE_JS,null);v.evaluateJavascript(ttsJs,null);ready=true;flush();}
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public void onShowCustomView(View v,CustomViewCallback cb){
    if(customView!=null){cb.onCustomViewHidden();return;}
    customView=v; customCb=cb;
    fsHolder=new FrameLayout(MainActivity.this); fsHolder.setBackgroundColor(Color.BLACK);
    fsHolder.addView(v,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
    ((ViewGroup)getWindow().getDecorView()).addView(fsHolder,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
    immersive(true);
   }
   @Override public void onShowCustomView(View v,int requestedOrientation,CustomViewCallback cb){onShowCustomView(v,cb);}
   @Override public void onHideCustomView(){hideCustom();}
   @Override public Bitmap getDefaultVideoPoster(){return Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);}
   @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
    if(chooser!=null)chooser.onReceiveValue(null); chooser=cb;
    Intent i=new Intent(Intent.ACTION_GET_CONTENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
    try{startActivityForResult(i,1);}catch(Exception e){chooser=null;return false;} return true;}
  });
  web.loadUrl("https://"+HOST+"/assets/index.html");
  handle(getIntent());
 }
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handle(i);}
 @SuppressWarnings("deprecation")
 void handle(Intent i){
  if(i==null)return; List<Uri> us=new ArrayList<>(); String a=i.getAction();
  if(Intent.ACTION_VIEW.equals(a)&&i.getData()!=null)us.add(i.getData());
  else if(Intent.ACTION_SEND.equals(a)){Uri u=i.getParcelableExtra(Intent.EXTRA_STREAM);if(u!=null)us.add(u);}
  else if(Intent.ACTION_SEND_MULTIPLE.equals(a)){ArrayList<Uri> l=i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(l!=null)us.addAll(l);}
  for(Uri u:us){String id=String.valueOf(seq++);opened.put(id,u);pending.add(new String[]{"https://"+HOST+"/opened/"+id,name(u),type(u)});}
  if(!us.isEmpty()){ext=true;web.setVisibility(View.INVISIBLE);web.postDelayed(()->web.setVisibility(View.VISIBLE),6000);}
  flush();
 }
 String name(Uri u){
  try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int k=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(k>=0&&c.getString(k)!=null)return c.getString(k);}}catch(Exception e){}
  String p=u.getLastPathSegment(); return p==null?"file":p;
 }
 String type(Uri u){String t=getContentResolver().getType(u);return t==null?"":t;}
 void poll(int n){
  web.evaluateJavascript("document.body&&document.body.classList.contains('ext')?1:0",v->{
   if("1".equals(v)||n>30)web.postDelayed(()->web.setVisibility(View.VISIBLE),250);
   else web.postDelayed(()->poll(n+1),100);
  });
 }
 void flush(){
  if(!ready)return;
  boolean any=!pending.isEmpty();
  if(any)poll(0);
  for(String[] p:pending)web.evaluateJavascript("openFromNative("+JSONObject.quote(p[0])+","+JSONObject.quote(p[1])+","+JSONObject.quote(p[2])+")",null);
  pending.clear();
 }
 @Override protected void onActivityResult(int rc,int res,Intent d){
  if(rc==4){
   if(res==RESULT_OK&&d!=null&&d.getData()!=null&&dlOut!=null){
    try(OutputStream o=getContentResolver().openOutputStream(d.getData()); InputStream in=new java.io.FileInputStream(dlOut)){byte[] b=new byte[1<<16]; int n; while((n=in.read(b))>0)o.write(b,0,n); o.flush(); Toast.makeText(this,"\u062a\u0645 \u062d\u0641\u0638 \u0627\u0644\u0635\u0648\u062a",Toast.LENGTH_SHORT).show();}
    catch(Exception ex){Toast.makeText(this,String.valueOf(ex.getMessage()),Toast.LENGTH_LONG).show();}
   }
   releaseAac();
   return;
  }
  if(rc==3){
   if(res==RESULT_OK&&d!=null&&d.getData()!=null)writeWav(d.getData());
   cleanDl();
   return;
  }
  if(rc==2){
   byte[] data=saveData; saveData=null;
   if(res==RESULT_OK&&d!=null&&d.getData()!=null&&data!=null){
    try(OutputStream o=getContentResolver().openOutputStream(d.getData())){o.write(data);o.flush();Toast.makeText(this,"\u062a\u0645 \u0627\u0644\u062d\u0641\u0638",Toast.LENGTH_SHORT).show();}
    catch(Exception e){Toast.makeText(this,String.valueOf(e.getMessage()),Toast.LENGTH_LONG).show();}
   }
   return;
  }
  if(rc!=1||chooser==null)return; Uri[] r=null;
  if(res==RESULT_OK&&d!=null){
   if(d.getClipData()!=null){int n=d.getClipData().getItemCount();r=new Uri[n];for(int k=0;k<n;k++)r[k]=d.getClipData().getItemAt(k).getUri();}
   else if(d.getData()!=null)r=new Uri[]{d.getData()};
  }
  chooser.onReceiveValue(r); chooser=null;
 }
 void ev(String id,String type){
  if(id.startsWith("dl:")){final boolean ok=type.equals("end");runOnUiThread(()->dlEvent(ok));return;}
  String[] p=id.split(":"); if(p.length<3)return;
  if(type.equals("end")&&!p[2].equals("1"))return;
  final String js="window.__ttsEv&&window.__ttsEv("+p[0]+",'"+type+"')";
  runOnUiThread(()->web.evaluateJavascript(js,null));
 }
 boolean langOk(String lang,String voice){
  try{
   if(voice!=null&&!voice.isEmpty()) for(Voice v:tts.getVoices()) if(v.getName().equals(voice))return true;
   if(lang==null||lang.isEmpty())return true;
   Locale l=Locale.forLanguageTag(lang); int r=tts.isLanguageAvailable(l);
   if(r<0&&!l.getLanguage().isEmpty())r=tts.isLanguageAvailable(new Locale(l.getLanguage()));
   return r>=0;
  }catch(Exception ex){return true;}
 }
 void promptVoiceInstall(String lang){
  String k=(lang==null||lang.isEmpty())?"-":Locale.forLanguageTag(lang).getLanguage();
  if(!ttsPrompted.add(k))return;
  Toast.makeText(this,"\u0627\u0644\u0635\u0648\u062a \u063a\u064a\u0631 \u0645\u062b\u0628\u0651\u062a \u2014 \u062b\u0628\u0651\u062a \u0628\u064a\u0627\u0646\u0627\u062a \u0627\u0644\u0635\u0648\u062a \u0645\u0646 \u0627\u0644\u0634\u0627\u0634\u0629 \u0627\u0644\u062a\u0627\u0644\u064a\u0629",Toast.LENGTH_LONG).show();
  try{startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  catch(Exception e1){try{startActivity(new Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception e2){}}
 }
 void setupVoice(String lang,String voice,float rate,float pitch){
  boolean set=false;
  try{ if(voice!=null&&!voice.isEmpty()) for(Voice v:tts.getVoices()) if(v.getName().equals(voice)){tts.setVoice(v);set=true;break;} }catch(Exception ex){}
  if(!set&&lang!=null&&!lang.isEmpty()){
   Locale l=Locale.forLanguageTag(lang); int r=tts.setLanguage(l);
   if(r<0&&!l.getLanguage().isEmpty())tts.setLanguage(new Locale(l.getLanguage()));
  }
  tts.setSpeechRate(rate>0?rate:1f); tts.setPitch(pitch>0?pitch:1f);
 }
 // ===== تنزيل الصوت: تحويل النص إلى ملف WAV =====
 void abortSave(){
  try{if(saveOut!=null)saveOut.close();}catch(Exception ex){}
  saveOut=null;
  try{if(saveUri!=null)getContentResolver().delete(saveUri,null,null);}catch(Exception ex){}
  saveUri=null;
 }
 void dlJs(String js){web.evaluateJavascript(js,null);}
 boolean notInstalled(Voice v){try{return v.getFeatures()!=null&&v.getFeatures().contains("notInstalled");}catch(Exception e){return false;}}
 void setupDlVoice(String lang,String voice,float rate,float pitch){
  try{
   java.util.Set<Voice> vs=tts.getVoices();
   if(vs!=null){
    Voice best=null,req=null;
    if(voice!=null&&!voice.isEmpty()) for(Voice v:vs) if(v.getName().equals(voice)) req=v;
    if(req!=null&&!req.isNetworkConnectionRequired()&&!notInstalled(req)) best=req;
    else{
     Locale l=Locale.forLanguageTag(lang==null?"":lang); int bq=Integer.MIN_VALUE;
     for(Voice v:vs){
      if(v.isNetworkConnectionRequired()||notInstalled(v))continue;
      Locale vl=v.getLocale(); if(!vl.getLanguage().equals(l.getLanguage()))continue;
      int sc=v.getQuality()+(vl.getCountry().equals(l.getCountry())?1000:0);
      if(sc>bq){bq=sc;best=v;}
     }
    }
    if(best!=null){tts.setVoice(best);tts.setSpeechRate(rate>0?rate:1f);tts.setPitch(pitch>0?pitch:1f);return;}
   }
  }catch(Exception ex){}
  setupVoice(lang,voice,rate,pitch);
 }
 void startDl(String text,String lang,String voice,float rate,float pitch,String name){
  if(!ttsReady||tts==null){Toast.makeText(this,"\u0645\u062d\u0631\u0643 \u0627\u0644\u0635\u0648\u062a \u063a\u064a\u0631 \u062c\u0627\u0647\u0632",Toast.LENGTH_LONG).show();return;}
  if(dlBusy){return;}
  if(text.trim().isEmpty())return;
  tts.stop(); ttsPending=null;
  if(!langOk(lang,voice)){promptVoiceInstall(lang);dlJs("window.__ttsDl&&window.__ttsDl(0,0,'novoice')");return;}
  setupDlVoice(lang,voice,rate,pitch);
  dlFails=0; dlErr=0; aac=null; aacTried=false; dlPieces.clear(); dlFiles.clear();
  int max=Math.max(400,Math.min(1500,TextToSpeech.getMaxSpeechInputLength()-200));
  int o=0;
  while(o<text.length()){
   int en=Math.min(text.length(),o+max);
   if(en<text.length()){int sp=Math.max(text.lastIndexOf('\n',en),text.lastIndexOf(' ',en));if(sp>o+max/3)en=sp;}
   String piece=text.substring(o,en).trim();
   if(!piece.isEmpty())dlPieces.add(piece);
   o=en;
  }
  if(dlPieces.isEmpty())return;
  File dir=new File(getCacheDir(),"ttsdl"); dir.mkdirs();
  File[] old=dir.listFiles(); if(old!=null)for(File f:old)f.delete();
  dlName=(name==null||name.isEmpty())?"audio":name;
  dlOut=new File(dir,"out.m4a");
  dlIdx=0; dlBusy=true; synthNext();
 }
 void synthNext(){
  if(dlIdx>=dlPieces.size()){finishDl();return;}
  File f=new File(new File(getCacheDir(),"ttsdl"),"p"+dlIdx+".wav");
  dlFiles.add(f);
  dlJs("window.__ttsDl&&window.__ttsDl("+dlIdx+","+dlPieces.size()+",'')");
  int r=tts.synthesizeToFile(dlPieces.get(dlIdx),new Bundle(),f,"dl:"+dlIdx);
  if(r!=TextToSpeech.SUCCESS){web.post(()->dlEvent(false));return;}
  final int tok=++dlTok;
  web.postDelayed(()->{if(dlBusy&&tok==dlTok)dlEvent(false);},60000);
 }
 void dlEvent(boolean ok){
  if(!dlBusy)return;
  dlTok++;
  final File f=new File(new File(getCacheDir(),"ttsdl"),"p"+dlIdx+".wav");
  if(!ok){
   dlFails++; f.delete(); dlFiles.remove(f);
   try{tts.stop();}catch(Exception ex){}
   dlIdx++; synthNext(); return;
  }
  final int myTok=dlTok;
  ioExec.execute(()->{
   boolean good=true;
   try{ good=consumePiece(f); }catch(Exception ex){ good=false; }
   final boolean g=good;
   runOnUiThread(()->{
    if(!dlBusy||myTok!=dlTok)return;
    if(!g){failDl();return;}
    dlIdx++; synthNext();
   });
  });
 }
 /* يحوّل ملف WAV لجزء واحد إلى AAC فوراً ويحذفه حتى لا يتضخّم التخزين */
 boolean consumePiece(File f)throws Exception{
  long[] in=wavInfo(f);
  if(in[1]<=0){f.delete();dlFiles.remove(f);dlFails++;return true;}
  if(!aacTried){
   aacTried=true;
   try{ aac=new AacEnc(dlOut,(int)in[2],(int)in[3]); }catch(Throwable t){ aac=null; }
  }
  if(aac==null)return true;               /* بدون ضغط: نُبقي الأجزاء ونجمعها WAV لاحقاً */
  try(RandomAccessFile r=new RandomAccessFile(f,"r")){
   r.seek(in[0]); long left=in[1]-(in[1]&1); byte[] buf=new byte[1<<15];
   while(left>0){int n=r.read(buf,0,(int)Math.min(buf.length,left)); if(n<=0)break; n&=~1; if(n>0)aac.feed(buf,0,n); left-=n;}
  }
  f.delete(); dlFiles.remove(f);
  return true;
 }
 void failDl(){
  dlBusy=false; dlTok++;
  try{tts.stop();}catch(Exception ex){}
  releaseAac(); cleanDl();
  dlJs("window.__ttsDl&&window.__ttsDl(0,0,'err',"+dlErr+")");
 }
 void dlAbort(){
  if(!dlBusy)return;
  dlBusy=false; dlTok++;
  try{tts.stop();}catch(Exception ex){}
  releaseAac(); cleanDl();
  dlJs("window.__scHide&&window.__scHide()");
 }
 void releaseAac(){ if(aac!=null){aac.release();aac=null;} try{if(dlOut!=null)dlOut.delete();}catch(Exception ex){} }
 void cleanDl(){for(File f:dlFiles)f.delete(); dlFiles.clear();}
 void finishDl(){
  dlBusy=false; dlTok++;
  if(aac!=null){
   ioExec.execute(()->{
    boolean ok=true;
    try{aac.finish();}catch(Exception ex){ok=false;}
    final boolean g=ok&&dlOut!=null&&dlOut.length()>0;
    runOnUiThread(()->{ if(!g){failDl();return;} saveAacResult(); });
   });
   return;
  }
  if(dlFiles.isEmpty()){failDl();return;}
  dlJs("window.__ttsDl&&window.__ttsDl(0,0,'ready')");
  Intent c=new Intent(Intent.ACTION_CREATE_DOCUMENT); c.addCategory(Intent.CATEGORY_OPENABLE); c.setType("audio/wav");
  c.putExtra(Intent.EXTRA_TITLE,dlName+".wav");
  try{startActivityForResult(c,3);}catch(Exception ex){cleanDl();}
 }
 /* حفظ ملف M4A مباشرة في مجلد التنزيلات (أندرويد 10+) أو عبر نافذة الاختيار في الأقدم */
 void saveAacResult(){
  final File src=dlOut; final String nm=dlName+".m4a";
  if(Build.VERSION.SDK_INT>=29){
   ioExec.execute(()->{
    String res=null;
    try{
     ContentValues cv=new ContentValues();
     cv.put(MediaStore.MediaColumns.DISPLAY_NAME,nm); cv.put(MediaStore.MediaColumns.MIME_TYPE,"audio/mp4");
     cv.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download"); cv.put(MediaStore.MediaColumns.IS_PENDING,1);
     Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
     if(u!=null){
      try(OutputStream o=getContentResolver().openOutputStream(u); InputStream in=new java.io.FileInputStream(src)){
       byte[] b=new byte[1<<16]; int n; while((n=in.read(b))>0)o.write(b,0,n); o.flush();
      }
      ContentValues c2=new ContentValues(); c2.put(MediaStore.MediaColumns.IS_PENDING,0); getContentResolver().update(u,c2,null,null);
      String shown=nm;
      try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())shown=c.getString(0);}catch(Exception e3){}
      JSONObject j=new JSONObject(); j.put("uri",u.toString()); j.put("name",shown); j.put("size",src.length()); res=j.toString();
     }
    }catch(Exception ex){res=null;}
    final String r=res;
    runOnUiThread(()->{
     if(r==null){failDl();return;}
     releaseAac();
     dlJs("window.__scDone&&window.__scDone("+JSONObject.quote(r)+")");
    });
   });
  }else{
   dlJs("window.__ttsDl&&window.__ttsDl(0,0,'ready')");
   Intent c=new Intent(Intent.ACTION_CREATE_DOCUMENT); c.addCategory(Intent.CATEGORY_OPENABLE); c.setType("audio/mp4");
   c.putExtra(Intent.EXTRA_TITLE,nm);
   try{startActivityForResult(c,4);}catch(Exception ex){releaseAac();}
  }
 }
 static class AacEnc{
  MediaCodec codec; MediaMuxer muxer; int track=-1; boolean started=false; long bytes=0; final int rate,ch;
  final MediaCodec.BufferInfo bi=new MediaCodec.BufferInfo();
  AacEnc(File out,int rate,int ch)throws Exception{
   this.rate=rate; this.ch=ch;
   MediaFormat f=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,rate,ch);
   f.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
   f.setInteger(MediaFormat.KEY_BIT_RATE,ch>1?96000:64000);
   f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384);
   codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
   codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start();
   if(out.exists())out.delete();
   muxer=new MediaMuxer(out.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
  }
  long pts(){return bytes*1000000L/((long)rate*ch*2);}
  void feed(byte[] b,int off,int len)throws Exception{
   while(len>0){
    int ii=codec.dequeueInputBuffer(10000);
    if(ii>=0){
     ByteBuffer ib=codec.getInputBuffer(ii); ib.clear(); int n=Math.min(len,ib.remaining());
     ib.put(b,off,n); codec.queueInputBuffer(ii,0,n,pts(),0); bytes+=n; off+=n; len-=n;
    }
    drain(0);
   }
  }
  boolean drain(long timeoutUs)throws Exception{
   while(true){
    int oi=codec.dequeueOutputBuffer(bi,timeoutUs);
    if(oi==MediaCodec.INFO_TRY_AGAIN_LATER)return false;
    if(oi==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;}
    else if(oi>=0){
     ByteBuffer ob=codec.getOutputBuffer(oi);
     if((bi.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)bi.size=0;
     if(bi.size>0&&started){ob.position(bi.offset);ob.limit(bi.offset+bi.size);muxer.writeSampleData(track,ob,bi);}
     codec.releaseOutputBuffer(oi,false);
     if((bi.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)return true;
    }
   }
  }
  void finish()throws Exception{
   int ii=-1,tries=0;
   while((ii=codec.dequeueInputBuffer(10000))<0&&tries++<500)drain(0);
   if(ii>=0)codec.queueInputBuffer(ii,0,0,pts(),MediaCodec.BUFFER_FLAG_END_OF_STREAM);
   long end=System.currentTimeMillis()+30000; boolean eos=false;
   while(!eos&&System.currentTimeMillis()<end)eos=drain(20000);
   release();
  }
  void release(){
   try{codec.stop();}catch(Exception e){} try{codec.release();}catch(Exception e){}
   try{if(started)muxer.stop();}catch(Exception e){} try{muxer.release();}catch(Exception e){}
  }
 }
 static int le16(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
 static long le32(byte[] b,int o){return (b[o]&255L)|((b[o+1]&255L)<<8)|((b[o+2]&255L)<<16)|((b[o+3]&255L)<<24);}
 static void put32(byte[] b,int o,long v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);b[o+2]=(byte)(v>>16);b[o+3]=(byte)(v>>24);}
 static void put16(byte[] b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);}
 long[] wavInfo(File f){
  try(RandomAccessFile r=new RandomAccessFile(f,"r")){
   long len=r.length(); if(len<12)return new long[]{0,0,22050,1,16};
   long pos=12; int rate=22050,ch=1,bits=16; byte[] c=new byte[8];
   while(pos+8<=len){
    r.seek(pos); r.readFully(c);
    String id=new String(c,0,4,"US-ASCII"); long sz=le32(c,4);
    if(id.equals("fmt ")){byte[] fm=new byte[16]; r.readFully(fm); ch=le16(fm,2); rate=(int)le32(fm,4); bits=le16(fm,14);}
    else if(id.equals("data")){long off=pos+8; long dl=sz; if(dl<=0||dl>len-off)dl=len-off; return new long[]{off,dl,rate,ch,bits};}
    pos+=8+sz+(sz&1);
   }
  }catch(Exception ex){}
  return new long[]{0,0,22050,1,16};
 }
 void writeWav(Uri uri){
  try(OutputStream o=new BufferedOutputStream(getContentResolver().openOutputStream(uri),1<<16)){
   List<long[]> infos=new ArrayList<>(); List<File> used=new ArrayList<>(); long total=0; long[] first=null;
   for(File f:dlFiles){long[] in=wavInfo(f); if(in[1]<=0)continue; if(first==null)first=in; infos.add(in); used.add(f); total+=in[1];}
   if(first==null||total<=0){Toast.makeText(this,"\u0644\u0645 \u064a\u064f\u0646\u062a\u062c \u0635\u0648\u062a",Toast.LENGTH_LONG).show();return;}
   int rate=(int)first[2],ch=(int)first[3],bits=(int)first[4];
   byte[] h=new byte[44];
   System.arraycopy("RIFF".getBytes("US-ASCII"),0,h,0,4); put32(h,4,36+total);
   System.arraycopy("WAVEfmt ".getBytes("US-ASCII"),0,h,8,8); put32(h,16,16); put16(h,20,1); put16(h,22,ch); put32(h,24,rate);
   put32(h,28,(long)rate*ch*bits/8); put16(h,32,ch*bits/8); put16(h,34,bits);
   System.arraycopy("data".getBytes("US-ASCII"),0,h,36,4); put32(h,40,total);
   o.write(h);
   byte[] buf=new byte[1<<16];
   for(int i=0;i<used.size();i++){
    long[] in=infos.get(i); long left=in[1];
    try(RandomAccessFile r=new RandomAccessFile(used.get(i),"r")){
     r.seek(in[0]);
     while(left>0){int n=r.read(buf,0,(int)Math.min(buf.length,left)); if(n<=0)break; o.write(buf,0,n); left-=n;}
    }
   }
   o.flush();
   Toast.makeText(this,"\u062a\u0645 \u062d\u0641\u0638 \u0627\u0644\u0635\u0648\u062a",Toast.LENGTH_SHORT).show();
  }catch(Exception ex){Toast.makeText(this,String.valueOf(ex.getMessage()),Toast.LENGTH_LONG).show();}
 }
 void doSpeak(int id,String text,String lang,String voice,float rate,float pitch,float vol){
  if(!langOk(lang,voice)){promptVoiceInstall(lang);ev(id+":0:1","error");return;}
  setupVoice(lang,voice,rate,pitch);
  Bundle pr=new Bundle(); pr.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,Math.max(0f,Math.min(1f,vol)));
  if(text.isEmpty()){ev(id+":0:1","end");return;}
  int o=0,k=0;
  while(o<text.length()){
   int en=Math.min(text.length(),o+3500);
   if(en<text.length()){int sp=text.lastIndexOf(' ',en);if(sp>o+1000)en=sp;}
   boolean last=en>=text.length();
   tts.speak(text.substring(o,en),k==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,pr,id+":"+k+":"+(last?1:0));
   o=en;k++;
  }
 }
 @Override protected void onStart(){super.onStart();visible=true;}
 @Override protected void onStop(){visible=false;super.onStop();}
 @Override protected void onDestroy(){if(cur==this)cur=null;MediaService.shutdown();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
 @SuppressWarnings("deprecation")
 void immersive(boolean on){
  View d=getWindow().getDecorView();
  d.setSystemUiVisibility(on?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN):View.SYSTEM_UI_FLAG_VISIBLE);
 }
 void hideCustom(){
  if(customView==null)return;
  try{((ViewGroup)getWindow().getDecorView()).removeView(fsHolder);}catch(Exception ex){}
  if(fsHolder!=null)fsHolder.removeAllViews();
  fsHolder=null; customView=null;
  try{if(customCb!=null)customCb.onCustomViewHidden();}catch(Exception ex){}
  customCb=null; immersive(false);
  try{setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}catch(Exception ex){}
 }
 @Override public void onBackPressed(){
  if(customView!=null){hideCustom();return;}
  try{
   web.evaluateJavascript("(window.__backClose&&window.__backClose())?1:0",v->{ if(!"1".equals(v))backDefault(); });
  }catch(Exception ex){backDefault();}
 }
 void backDefault(){
  if(ext){if(MediaService.playing){moveTaskToBack(true);}else finishAndRemoveTask();return;}
  if(web.canGoBack())web.goBack();else super.onBackPressed();
 }

 public static class ShareProvider extends android.content.ContentProvider{
  File file(Uri u){String n=u.getLastPathSegment(); if(n==null)return null; return new File(new File(getContext().getCacheDir(),"share"),n.replace('/','_'));}
  @Override public boolean onCreate(){return true;}
  @Override public android.os.ParcelFileDescriptor openFile(Uri u,String mode) throws java.io.FileNotFoundException{
   File f=file(u); if(f==null||!f.exists())throw new java.io.FileNotFoundException();
   return android.os.ParcelFileDescriptor.open(f,android.os.ParcelFileDescriptor.MODE_READ_ONLY);
  }
  @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){
   File f=file(u); if(f==null)return null;
   String[] cols=p!=null?p:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
   android.database.MatrixCursor c=new android.database.MatrixCursor(cols); Object[] row=new Object[cols.length];
   for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=f.getName();else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();}
   c.addRow(row); return c;
  }
  @Override public String getType(Uri u){
   File f=file(u); String n=f==null?"":f.getName(); int d=n.lastIndexOf('.'); String ext=d>=0?n.substring(d+1).toLowerCase():"";
   String m=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext); return m!=null?m:"application/octet-stream";
  }
  @Override public Uri insert(Uri u,ContentValues v){return null;}
  @Override public int delete(Uri u,String s,String[] a){return 0;}
  @Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
 }

 /* ===== خدمة التشغيل في الخلفية: إشعار تحكم + أزرار شاشة القفل ===== */
 public static class MediaService extends android.app.Service{
  static MediaService inst; static volatile boolean playing=false;
  static final String CH="fr_media", A_UPD="app.filereader.UPD", A_BTN="app.filereader.BTN";
  final BroadcastReceiver rx=new BroadcastReceiver(){
   @Override public void onReceive(Context c,Intent in){
    String cc=in==null?null:in.getStringExtra("c");
    if(cc==null)return;
    if("stop".equals(cc)){cmd("stop");stopAll();}
    else cmd(cc);
   }
  };
  MediaSession sess; NotificationManager nm; PowerManager.WakeLock wl; Bitmap art;
  String title="",sub=""; long pos=0,dur=0; boolean fg=false; boolean pl=false;
  @Override public android.os.IBinder onBind(Intent i){return null;}
  @Override public void onCreate(){
   super.onCreate(); inst=this;
   try{ IntentFilter fl=new IntentFilter(A_BTN); if(Build.VERSION.SDK_INT>=33)registerReceiver(rx,fl,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(rx,fl); }catch(Exception e){}
   nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
   if(Build.VERSION.SDK_INT>=26){
    NotificationChannel c=new NotificationChannel(CH,"\u062a\u0634\u063a\u064a\u0644 \u0627\u0644\u0645\u0648\u0633\u064a\u0642\u0649",NotificationManager.IMPORTANCE_LOW); c.setShowBadge(false); c.setSound(null,null);
    nm.createNotificationChannel(c);
   }
   try{art=BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher);}catch(Exception e){}
   sess=new MediaSession(this,"FileReader");
   sess.setCallback(new MediaSession.Callback(){
    @Override public void onPlay(){cmd("play");}
    @Override public void onPause(){cmd("pause");}
    @Override public void onSkipToNext(){cmd("next");}
    @Override public void onSkipToPrevious(){cmd("prev");}
    @Override public void onStop(){cmd("stop");}
    @Override public void onSeekTo(long p){cmd("seek:"+p);}
   });
   sess.setActive(true);
   PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
   wl=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"filereader:play"); wl.setReferenceCounted(false);
  }
  static void cmd(final String c){
   final MainActivity a=MainActivity.cur; if(a==null||a.web==null)return;
   a.runOnUiThread(()->a.web.evaluateJavascript("window.__mediaCmd&&window.__mediaCmd("+JSONObject.quote(c)+")",null));
  }
  static void push(Context c,String t,String s,boolean p,long pos,long dur){
   if(inst!=null){inst.apply(t,s,p,pos,dur);return;}
   if(!p)return;
   Intent i=new Intent(c,MediaService.class).setAction(A_UPD).putExtra("t",t).putExtra("s",s).putExtra("p",p).putExtra("pos",pos).putExtra("d",dur);
   try{ if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i); else c.startService(i); }catch(Exception e){}
  }
  static void shutdown(){ if(inst!=null)inst.stopAll(); playing=false; }
  @Override public int onStartCommand(Intent in,int flags,int id){
   if(in!=null){
    String a=in.getAction();
    if(A_UPD.equals(a)) apply(in.getStringExtra("t"),in.getStringExtra("s"),in.getBooleanExtra("p",false),in.getLongExtra("pos",0),in.getLongExtra("d",0));
    else if(A_BTN.equals(a)){
     String c=in.getStringExtra("c");
     if("stop".equals(c)){cmd("stop");stopAll();}
     else if(c!=null)cmd(c);
    }
   }
   if(in!=null&&A_UPD.equals(in.getAction())&&!fg){try{startForegroundCompat(build());fg=true;}catch(Exception e){}}
   return START_NOT_STICKY;
  }
  void startForegroundCompat(Notification n){
   if(Build.VERSION.SDK_INT>=29)startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); else startForeground(7,n);
  }
  void apply(String t,String s,boolean p,long ps,long d){
   title=t==null?"":t; sub=s==null?"":s; pl=p; pos=Math.max(0,ps); dur=Math.max(0,d); playing=p;
   try{
    sess.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putString(MediaMetadata.METADATA_KEY_ARTIST,sub).putLong(MediaMetadata.METADATA_KEY_DURATION,dur).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,art).build());
    long acts=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SEEK_TO;
    sess.setPlaybackState(new PlaybackState.Builder().setActions(acts).setState(p?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,pos,p?1f:0f,SystemClock.elapsedRealtime()).build());
   }catch(Exception e){}
   Notification n=build();
   try{
    if(p){
     if(!fg){startForegroundCompat(n);fg=true;} else nm.notify(7,n);
     if(!wl.isHeld())wl.acquire(6*60*60*1000L);
    }else{
     if(fg){stopForeground(false);fg=false;}
     nm.notify(7,n);
     if(wl.isHeld())wl.release();
    }
   }catch(Exception e){}
  }
  PendingIntent btn(String c,int code){
   Intent i=new Intent(A_BTN).setPackage(getPackageName()).putExtra("c",c);
   return PendingIntent.getBroadcast(this,code,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  }
  Notification build(){
   Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CH):new Notification.Builder(this);
   PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   b.setSmallIcon(android.R.drawable.stat_sys_headset).setContentTitle(title.isEmpty()?"\u0642\u0627\u0631\u0626 \u0627\u0644\u0645\u0644\u0641\u0627\u062a \u0627\u0644\u0634\u0627\u0645\u0644":title).setContentText(sub)
    .setContentIntent(open).setDeleteIntent(btn("stop",9)).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setShowWhen(false).setOngoing(pl);
   if(art!=null)b.setLargeIcon(art);
   b.addAction(new Notification.Action.Builder(Icon.createWithResource(this,android.R.drawable.ic_media_previous),"\u0627\u0644\u0633\u0627\u0628\u0642",btn("prev",1)).build());
   b.addAction(pl?new Notification.Action.Builder(Icon.createWithResource(this,android.R.drawable.ic_media_pause),"\u0625\u064a\u0642\u0627\u0641 \u0645\u0624\u0642\u062a",btn("pause",2)).build()
                 :new Notification.Action.Builder(Icon.createWithResource(this,android.R.drawable.ic_media_play),"\u062a\u0634\u063a\u064a\u0644",btn("play",3)).build());
   b.addAction(new Notification.Action.Builder(Icon.createWithResource(this,android.R.drawable.ic_media_next),"\u0627\u0644\u062a\u0627\u0644\u064a",btn("next",4)).build());
   b.addAction(new Notification.Action.Builder(Icon.createWithResource(this,android.R.drawable.ic_menu_close_clear_cancel),"\u0625\u064a\u0642\u0627\u0641 \u0648\u0625\u063a\u0644\u0627\u0642",btn("stop",5)).build());
   b.setStyle(new Notification.MediaStyle().setMediaSession(sess.getSessionToken()).setShowActionsInCompactView(0,1,2));
   return b.build();
  }
  void stopAll(){
   try{if(wl!=null&&wl.isHeld())wl.release();}catch(Exception e){}
   try{stopForeground(true);}catch(Exception e){}
   try{nm.cancel(7);}catch(Exception e){}
   fg=false; pl=false; playing=false;
   try{stopSelf();}catch(Exception e){}
  }
  @Override public void onTaskRemoved(Intent rootIntent){stopAll();super.onTaskRemoved(rootIntent);}
  @Override public void onDestroy(){
   try{if(wl!=null&&wl.isHeld())wl.release();}catch(Exception e){}
   try{if(sess!=null){sess.setActive(false);sess.release();}}catch(Exception e){}
   try{unregisterReceiver(rx);}catch(Exception e){}
   inst=null; playing=false; super.onDestroy();
  }
 }
}
