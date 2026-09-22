package com.smartglasses.ferry20;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import java.lang.reflect.*;
import java.io.FileOutputStream;

/** Separate instrumentation APK; never included in the playable APK. */
public class FerryInstrumentation extends Instrumentation {
 MainActivity activity;
 MainActivity.Game g;
 int passed,failed;
 boolean baseline;
 StringBuilder report=new StringBuilder();
 interface Check {void run() throws Exception;}
 public void onCreate(Bundle args){super.onCreate(args);baseline=args!=null&&"true".equals(args.getString("baseline"));start();}
 void ui(final Check c) throws Exception {final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{c.run();}catch(Throwable t){error[0]=t;}}});if(error[0]!=null)throw new Exception(error[0]);}
 void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 void test(String name,Check c){try{c.run();passed++;report.append("PASS ").append(name).append('\n');}catch(Throwable t){failed++;report.append("FAIL ").append(name).append(": ").append(t).append('\n');}Bundle b=new Bundle();b.putString("stream",report.substring(report.lastIndexOf("\n",report.length()-2)+1));sendStatus(0,b);}
 void focus() throws Exception {boolean[] focused={false};ui(()->focused[0]=activity.hasWindowFocus());if(focused[0])return;getTargetContext().startActivity(new Intent().setClassName(getTargetContext(),"com.smartglasses.ferry20.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));long until=SystemClock.uptimeMillis()+3000;while(SystemClock.uptimeMillis()<until){ui(()->focused[0]=activity.hasWindowFocus());if(focused[0])return;SystemClock.sleep(50);}check(false,"Game window did not regain focus; external input not injected");}
 void fresh() throws Exception {focus();ui(()->{g.state=0;g.lastTap=0;g.active=true;g.pauseAt=0;g.tap();});}
 void ready(float error) throws Exception {ui(()->{g.feedbackEnd=0;g.lastTap=0;g.speed=0;g.phase=(float)(Math.asin(error/174.0)/Math.PI);});}
 void systemTouch() throws Exception {focus();long n=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(n,n,0,240,300,0),up=MotionEvent.obtain(n,n+20,1,240,300,0);sendPointerSync(down);sendPointerSync(up);down.recycle();up.recycle();waitForIdleSync();}
 // Rapid synthetic device-wide taps can invoke the glasses' global double-tap BACK gesture.
 // Scoring fixtures use the Activity's normal touch dispatch; system integration is tested once separately.
 void touch() throws Exception {ui(()->{long n=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(n,n,0,240,300,0),up=MotionEvent.obtain(n,n+20,1,240,300,0);activity.dispatchTouchEvent(down);activity.dispatchTouchEvent(up);down.recycle();up.recycle();});waitForIdleSync();}
 AudioTrack[] tracks() throws Exception {Field f=g.getClass().getDeclaredField("audio");f.setAccessible(true);Object bank=f.get(g);Field tf=bank.getClass().getDeclaredField("tracks");tf.setAccessible(true);return (AudioTrack[])tf.get(bank);}
 void sound(String method,int value) throws Exception {Field f=g.getClass().getDeclaredField("audio");f.setAccessible(true);Object bank=f.get(g);Method m=bank.getClass().getDeclaredMethod(method,int.class);m.setAccessible(true);m.invoke(bank,value);}
 void audioIdle() throws Exception {Field f=g.getClass().getDeclaredField("audio");f.setAccessible(true);Object bank=f.get(g);Field w=bank.getClass().getDeclaredField("worker");w.setAccessible(true);((java.util.concurrent.ExecutorService)w.get(bank)).submit(()->{}).get(3,java.util.concurrent.TimeUnit.SECONDS);}
 void shot(String name) throws Exception {waitForIdleSync();SystemClock.sleep(180);Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"screenshot unavailable");try(FileOutputStream out=new FileOutputStream(getTargetContext().getExternalFilesDir(null)+"/"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 public void onStart(){SharedPreferences prefs=getTargetContext().getSharedPreferences("ferry",0);boolean had=prefs.contains("best");int original=prefs.getInt("best",0);
 try {
 activity=(MainActivity)startActivitySync(new Intent().setClassName(getTargetContext(),"com.smartglasses.ferry20.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));g=activity.game;
 test("pause preserves ship position and input cooldown",()->{fresh();final long[] before=new long[3];ui(()->{before[0]=g.start;before[1]=(long)g.offset;before[2]=g.feedbackEnd;callActivityOnPause(activity);});SystemClock.sleep(330);ui(()->{callActivityOnResume(activity);long delta=g.start-before[0];check(delta>=300,"timer did not pause");check(Math.abs(((long)g.offset-before[1])-delta)<=2,"ship position advanced during pause");check(Math.abs((g.feedbackEnd-before[2])-delta)<=2,"cooldown advanced during pause");});});
 test("BGM octave is preserved",()->{ui(()->{g.state=0;sound("music",11);});audioIdle();check(tracks()[5].getPlaybackRate()==44100,"expected octave 44100, actual "+tracks()[5].getPlaybackRate());});
 if(!baseline){
 test("system-injected touch starts a round",()->{ui(()->{g.state=0;g.lastTap=0;});systemTouch();ui(()->check(g.state==1&&g.score==0&&g.combo==0,"bad initial state"));});
 test("central touch gives exactly 100",()->{fresh();ready(0);touch();ui(()->check(g.score==100&&g.combo==1&&g.perfect==1&&g.hits==1,"central score"));shot("perfect");});
 test("success bonus and miss resets combo",()->{ready(40);touch();ui(()->check(g.score==160&&g.combo==2&&g.hits==2,"second success should add 60"));ready(100);touch();ui(()->check(g.score==160&&g.combo==0&&g.hits==2,"miss changed score or failed reset"));shot("miss");ready(0);touch();ui(()->check(g.score==260&&g.combo==1,"bonus did not reset"));});
 test("both sides of scoring boundaries",()->{float[] errors={-75.1f,-74.9f,-30.1f,-29.9f,29.9f,30.1f,74.9f,75.1f};int[] points={0,50,50,100,100,50,50,0};for(int i=0;i<errors.length;i++){fresh();ready(errors[i]);touch();final int expected=points[i];ui(()->check(g.score==expected,"boundary expected "+expected+" actual "+g.score));}});
 test("combo bonus caps at 100",()->{fresh();for(int i=0;i<13;i++){ready(0);touch();}ui(()->check(g.score==2050&&g.combo==13,"13 perfects should total 2050: "+g.score));});
 test("rapid duplicate inputs cannot double score",()->{fresh();ready(0);ui(()->{long n=g.now();for(int i=0;i<5;i++){MotionEvent e=MotionEvent.obtain(n,n,MotionEvent.ACTION_UP,240,300,0);g.onTouchEvent(e);e.recycle();}check(g.score==100&&g.hits==1&&g.combo==1&&g.port==2,"duplicate changed round");});});
 test("cancelled touch does not activate",()->{fresh();ready(0);ui(()->{long n=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(n,n,0,240,300,0),cancel=MotionEvent.obtain(n,n+20,3,240,300,0);activity.dispatchTouchEvent(down);activity.dispatchTouchEvent(cancel);down.recycle();cancel.recycle();check(g.hits==0&&g.score==0,"cancel counted as tap");});});
 test("enter and dpad-center both work",()->{fresh();ready(0);sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);ui(()->check(g.score==100,"ENTER not accepted"));ready(0);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);ui(()->check(g.score==210,"DPAD_CENTER not accepted"));});
 test("Rokid contact acts immediately; release and delayed ENTER are ignored",()->{fresh();ready(0);int rokid=-1;for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&d.getName().startsWith("ROKID,"))rokid=id;}check(rokid>=0,"Rokid touchpad not found");final int device=rokid;ui(()->{long n=g.now();KeyEvent contact=new KeyEvent(n,n,KeyEvent.ACTION_DOWN,83,0,0,device,204,0,InputDevice.SOURCE_KEYBOARD);g.onKeyDown(83,contact);check(g.score==100&&g.hits==1,"contact did not score immediately");g.feedbackEnd=0;g.lastTap=0;g.onKeyUp(83,new KeyEvent(n,n+24,KeyEvent.ACTION_UP,83,0,0,device,204,0,InputDevice.SOURCE_KEYBOARD));check(g.score==100&&g.hits==1,"release double counted");KeyEvent delayed=new KeyEvent(n+500,n+524,KeyEvent.ACTION_UP,66,0,0,device,28,0,InputDevice.SOURCE_KEYBOARD);g.onKeyUp(66,delayed);check(g.score==100&&g.hits==1&&g.port==2,"paired ENTER double counted");});});
 test("other devices notification key does not play",()->{fresh();ready(0);ui(()->{long n=g.now();g.onKeyUp(83,new KeyEvent(n,n,KeyEvent.ACTION_UP,83,0,0,-1,204,0,InputDevice.SOURCE_KEYBOARD));check(g.score==0&&g.hits==0,"unrelated notification triggered game");});});
 test("expired input cannot score or immediately restart",()->{fresh();ready(0);ui(()->{g.start=g.now()-20000;g.tap();check(g.state==2&&g.score==0,"late tap scored");g.tap();check(g.state==2,"result skipped");});});
 test("replay clears prior round counters",()->{SystemClock.sleep(750);touch();ui(()->check(g.state==1&&g.score==0&&g.combo==0&&g.hits==0&&g.perfect==0&&g.port==1,"stale result data"));});
 test("all six audio tracks produce playback progress",()->{for(int i=0;i<6;i++){final int index=i;ui(()->sound("play",index));audioIdle();SystemClock.sleep(50);check(tracks()[i]!=null&&tracks()[i].getState()==AudioTrack.STATE_INITIALIZED,"track init "+i);check(tracks()[i].getPlaybackHeadPosition()>0,"no PCM playback progress "+i);}ui(()->g.pause());audioIdle();for(AudioTrack t:tracks())check(t.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING,"audio continued when paused");ui(()->g.resume());});
 test("real 20-second round plus background pause",()->{fresh();ready(0);touch();ui(()->{g.speed=1;});long wall=SystemClock.uptimeMillis();SystemClock.sleep(1100);final long[] paused=new long[1];ui(()->{callActivityOnPause(activity);paused[0]=g.now();});SystemClock.sleep(800);ui(()->callActivityOnResume(activity));long deadline=wall+24000;boolean[] done={false};while(SystemClock.uptimeMillis()<deadline){ui(()->done[0]=g.state==2);if(done[0])break;SystemClock.sleep(50);}ui(()->{long elapsed=g.ended-g.start;check(g.state==2,"round never finished");check(elapsed>=20000&&elapsed<20200,"active elapsed "+elapsed);check(g.score==100,"score changed without input");report.append("MEASURE active_ms="+elapsed+" wall_ms="+(g.ended-wall)+"\n");});shot("result");});
 test("best survives activity recreation and lower scores",()->{ui(()->{g.best=0;g.score=777;g.finish();});check(prefs.getInt("best",0)==777,"high score not saved");ui(()->{g.score=100;g.finish();check(g.best==777,"lower score replaced best");MainActivity.Game reloaded=new MainActivity.Game(activity);check(reloaded.best==777,"recreated game lost best");reloaded.pause();Field f=reloaded.getClass().getDeclaredField("audio");f.setAccessible(true);Object audio=f.get(reloaded);Method release=audio.getClass().getDeclaredMethod("release");release.setAccessible(true);release.invoke(audio);});});
 }
 }catch(Throwable t){failed++;report.append("FATAL "+t+"\n");}
 finally {SharedPreferences.Editor edit=prefs.edit();if(had)edit.putInt("best",original);else edit.remove("best");edit.commit();try{ui(()->{g.best=original;g.state=0;g.pause();activity.finish();});}catch(Exception ignored){}Bundle result=new Bundle();result.putString("stream","\n"+report+"TOTAL passed="+passed+" failed="+failed+"\nUser high score restored="+original+"\n");try(FileOutputStream out=new FileOutputStream(getTargetContext().getExternalFilesDir(null)+"/verification-complete.txt")){out.write(result.getString("stream").getBytes("UTF-8"));}catch(Exception io){android.util.Log.e("FerryTests","Cannot persist results",io);}finish(failed==0?-1:0,result);}
 }
}
