package com.smartglasses.ferry20;
import android.app.Activity;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.util.Log;
import android.view.*;
import java.util.Random;

public class MainActivity extends Activity {
 Game game;
 public void onCreate(Bundle b){super.onCreate(b);requestWindowFeature(1);getWindow().setFlags(1024,1024);getWindow().addFlags(128);getWindow().getDecorView().setSystemUiVisibility(5894);game=new Game(this);setContentView(game);setVolumeControlStream(AudioManager.STREAM_MUSIC);}
 protected void onPause(){super.onPause();game.pause();}
 protected void onResume(){super.onResume();if(game!=null)game.resume();}
 protected void onDestroy(){game.pause();game.audio.release();super.onDestroy();}
 static class Game extends View {
 static final String TAG="Ferry20";
 Paint p=new Paint(3); Random rnd=new Random(); ToneBank audio=new ToneBank();
 int state=0,score=0,combo=0,best=0,hits=0,perfect=0,port=1; long start,lastTap,feedbackEnd,ended,pauseAt,beatAt,offset,lastRokidContact,lastRokidDown=-1; int beat=0,lastRokidDevice=-1;
 float phase=0,speed=1,frozenX=240; long stoppedAt; String feedback=""; boolean active=true,newBest=false,judged=false,stopFramePending=false;
 final int green=Color.rgb(170,255,190),bright=Color.rgb(80,255,135),dim=Color.rgb(55,132,85);
 Game(Context c){super(c);setFocusableInTouchMode(true);requestFocus();best=c.getSharedPreferences("ferry",0).getInt("best",0);}
 long now(){return SystemClock.uptimeMillis();}
 void pause(){if(active){pauseAt=now();active=false;}audio.pause();Log.i(TAG,"PAUSE state="+state);}
 void resume(){long n=now();if(pauseAt>0){long delta=n-pauseAt;if(state==1){start+=delta;offset+=delta;feedbackEnd+=delta;lastTap+=delta;}else if(state==2){ended+=delta;}}pauseAt=0;active=true;beatAt=n;invalidate();Log.i(TAG,"RESUME state="+state);}
 void next(){phase=rnd.nextFloat()*0.45f;offset=feedbackEnd;speed=0.60f+rnd.nextFloat()*0.15f+Math.min(0.20f,hits*0.02f);}
 float x(long n){return 240+(float)Math.sin((Math.max(0L,n-offset)/1000.0*speed+phase)*Math.PI)*174;}
 void tap(){if(!active)return;long n=now();if(n-lastTap<180)return;lastTap=n;
 if(state!=1){if(state==2&&n-ended<700)return;score=combo=hits=perfect=0;port=1;newBest=false;judged=false;state=1;start=n;beatAt=n;beat=0;feedback="出航！";feedbackEnd=n+650;next();audio.play(0);Log.i(TAG,"START 20000ms best="+best);}
 else {if(n-start>=20000){finish();return;}if(n<feedbackEnd)return;frozenX=x(n);judged=true;float e=Math.abs(frozenX-240);if(e<=75){boolean exact=e<=30;combo++;hits++;if(exact)perfect++;int points=(exact?100:50)+Math.min(100,(combo-1)*10);score+=points;feedback=(exact?"ぴったり！":"着岸成功！")+" +"+points;audio.play(exact?2:1,1+Math.min(10,combo)*0.025f);}else{combo=0;feedback="通り過ぎた！  次の港へ";audio.play(3);}port++;feedbackEnd=n+650;next();Log.i(TAG,"TAP error="+e+" score="+score+" combo="+combo+" hits="+hits+" perfect="+perfect);}
 if(state==1&&judged&&n<feedbackEnd){stoppedAt=n;stopFramePending=true;}invalidate();}
 void finish(){state=2;ended=now();newBest=score>best;if(newBest){best=score;getContext().getSharedPreferences("ferry",0).edit().putInt("best",best).apply();}audio.play(4);Log.i(TAG,"FINISH score="+score+" hits="+hits+" elapsed="+(now()-start));}
 void text(Canvas c,String s,float y,float size,int color){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(Paint.Align.CENTER);p.setStyle(Paint.Style.FILL);c.drawText(s,240,y,p);}
 void line(Canvas c,float a,float b,float d,float e,int col,float w){p.setColor(col);p.setStrokeWidth(w);c.drawLine(a,b,d,e,p);}
 void boat(Canvas c,float x,float y){p.setColor(green);p.setStyle(Paint.Style.FILL);Path q=new Path();q.moveTo(x-61,y);q.lineTo(x+65,y);q.lineTo(x+44,y+28);q.lineTo(x-45,y+28);q.close();c.drawPath(q,p);c.drawRoundRect(x-39,y-25,x+36,y-3,4,4,p);c.drawRect(x-14,y-42,x+18,y-27,p);c.drawRect(x+8,y-56,x+19,y-43,p);p.setColor(Color.BLACK);for(int i=0;i<5;i++)c.drawRect(x-30+i*13,y-18,x-23+i*13,y-10,p);line(c,x-70,y+36,x+52,y+36,dim,2);}
 protected void onDraw(Canvas c){c.drawColor(Color.BLACK);float sc=Math.min(getWidth()/480f,getHeight()/640f);c.save();c.translate((getWidth()-480*sc)/2,(getHeight()-640*sc)/2);c.scale(sc,sc);long n=now();
 if(active&&state==1&&n-start>=20000)finish();
 text(c,"F E R R Y  2 0",65,28,green);
 if(state==0){text(c,"20秒・ぴったり着岸チャレンジ",105,20,green);boat(c,240,248);text(c,"フェリーが港の真ん中に来たら",350,23,green);text(c,"タップで着岸！",388,30,bright);text(c,"中央で100点・連続成功でボーナス",443,19,green);text(c,"タップして出航",533,29,bright);text(c,"BEST  "+best,587,19,dim);}
 else if(state==1){int sec=(int)Math.ceil((20000-(n-start))/1000.0);text(c,"SCORE "+score+"     残り "+sec+" 秒",111,25,green);text(c,"連続 "+combo+" 回   /   第 "+port+" 港",149,19,green);
 p.setColor(Color.rgb(14,55,32));c.drawRect(165,211,315,415,p);p.setColor(Color.rgb(29,100,53));c.drawRect(210,211,270,415,p);line(c,240,216,240,415,green,2);text(c,"港の中央",195,20,bright);
 for(int j=0;j<5;j++){float yy=335+j*23;for(int i=0;i<7;i++){float xx=i*80-(n/30%80);line(c,xx,yy,xx+32,yy,dim,1);}}
 float shipX=judged&&n<feedbackEnd?frozenX:x(n);boat(c,shipX,292);if(stopFramePending){Log.i(TAG,"STOP_FRAME afterInputMs="+(n-stoppedAt)+" x="+shipX);stopFramePending=false;}line(c,shipX,326,shipX,337,bright,3);line(c,163,420,317,420,green,7);line(c,174,410,174,440,green,5);line(c,306,410,306,440,green,5);
 text(c,n<feedbackEnd?feedback:"中央に来たらタップ！",494,n<feedbackEnd?24:23,bright);text(c,"中央100点 / 港の内側50点",548,18,green);
 line(c,50,585,430,585,dim,4);line(c,50,585,50+380*Math.max(0,1-(n-start)/20000f),585,bright,5);
 if(active&&n>=beatAt){audio.music(beat++);beatAt=n+250;}}
 else{text(c,"航海完了！",134,33,green);text(c,""+score,224,65,bright);text(c,newBest?"NEW BEST！ 記録更新":"BEST  "+best,263,23,green);boat(c,240,343);text(c,"着岸 "+hits+" 回   /   ぴったり "+perfect+" 回",417,22,green);text(c,score>=1200?"称号：伝説の船長":score>=600?"称号：ベテラン船長":"称号：かけだし船長",466,25,bright);text(c,"タップでもう一度",550,28,green);text(c,"次は、もっと中央を狙おう",589,18,dim);}
 c.restore();if(active)postInvalidateDelayed(16);}
 public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_UP){Log.i(TAG,"INPUT touch device="+e.getDeviceId());performClick();}return true;}
 public boolean performClick(){super.performClick();tap();return true;}
 boolean primary(int k){return k==23||k==66||k==62||k==96;}
 // This device emits scan 204 immediately, then ENTER about 500 ms later.
 // Use the contact event only for the verified Rokid touchpad, and discard its paired ENTER.
 boolean rokidContact(int k,KeyEvent e){InputDevice d=e.getDevice();return k==KeyEvent.KEYCODE_NOTIFICATION&&e.getScanCode()==204&&d!=null&&d.getName().startsWith("ROKID,");}
 void contact(KeyEvent e){lastRokidContact=e.getEventTime();lastRokidDown=e.getDownTime();lastRokidDevice=e.getDeviceId();Log.i(TAG,"ROKID immediate contact eventAgeMs="+(now()-e.getEventTime()));tap();}
 public boolean onKeyDown(int k,KeyEvent e){if(rokidContact(k,e)){if(e.getRepeatCount()==0&&!e.isCanceled())contact(e);return true;}return primary(k)||super.onKeyDown(k,e);}
 public boolean onKeyUp(int k,KeyEvent e){Log.i(TAG,"INPUT key="+k+" device="+e.getDeviceId()+" scan="+e.getScanCode());if(rokidContact(k,e)){if(!e.isCanceled()&&e.getDownTime()!=lastRokidDown)contact(e);return true;}if(primary(k)){long delta=e.getEventTime()-lastRokidContact;if(k==KeyEvent.KEYCODE_ENTER&&e.getDeviceId()==lastRokidDevice&&lastRokidContact>0&&delta>=0&&delta<900){Log.i(TAG,"ROKID paired ENTER ignored delayMs="+delta);return true;}if(!e.isCanceled())tap();return true;}return super.onKeyUp(k,e);}
 }
         private static final class ToneBank {
            static final int START = 0;
            static final int GOOD = 1;
            static final int PERFECT = 2;
            static final int MISS = 3;
            static final int FINISH = 4;
            private static final String TAG="Ferry20Audio"; private static final int SAMPLE_RATE = 22_050;
            private final AudioTrack[] tracks = new AudioTrack[6];
            private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
            private volatile int generation;
            private volatile boolean released;

            ToneBank() {
                try {
                    tracks[5] = buildTone(523d,523d,200,0.14f,true); tracks[START] = buildTone(220d, 440d, 170, 0.42f, false);
                    tracks[GOOD] = buildTone(520d, 760d, 120, 0.47f, false);
                    tracks[PERFECT] = buildTone(660d, 1_180d, 170, 0.54f, true);
                    tracks[MISS] = buildTone(210d, 85d, 190, 0.38f, false);
                    tracks[FINISH] = buildTone(880d, 440d, 300, 0.48f, true);
                    Log.d(TAG, "synth audio ready sampleRate=" + SAMPLE_RATE);
                } catch (Throwable error) {
                    Log.w(TAG, "audio unavailable; game continues silently", error);
                    release();
                }
            }

            private AudioTrack buildTone(double startHz, double endHz, int durationMs,
                                         float volume, boolean harmonic) {
                int count = Math.max(1, SAMPLE_RATE * durationMs / 1000);
                short[] pcm = new short[count];
                double phase = 0.0;
                for (int i = 0; i < count; i++) {
                    float t = i / (float) count;
                    double hz = startHz + (endHz - startHz) * t;
                    phase += (Math.PI * 2.0 * hz) / SAMPLE_RATE;
                    double sample = Math.sin(phase) * 0.82;
                    if (harmonic) {
                        sample += Math.sin(phase * 2.01) * 0.18;
                    }
                    float attack = Math.min(1f, i / (SAMPLE_RATE * 0.010f));
                    float release = Math.min(1f, (count - i) / (SAMPLE_RATE * 0.035f));
                    pcm[i] = (short) (sample * volume * attack * release * 32767f);
                }
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        pcm.length * 2, AudioTrack.MODE_STATIC);
                int written = track.write(pcm, 0, pcm.length);
                if (written != pcm.length) {
                    track.release();
                    throw new IllegalStateException("short PCM write " + written + "/" + pcm.length);
                }
                return track;
            }

            void play(int which) {
                play(which, 1f);
            }

            void play(final int which, final float pitch) {
                final int expectedGeneration=generation;
                if(released)return;
                try {worker.execute(new Runnable(){public void run(){playNow(which,pitch,expectedGeneration);}});}
                catch(java.util.concurrent.RejectedExecutionException ignored) { }
            }

            // Native audio operations can block for >100 ms on these glasses.
            // Keep them off the UI thread so the ferry freezes on the next frame.
            private synchronized void playNow(int which, float pitch, int expectedGeneration) {
                if(released||expectedGeneration!=generation)return;
                if (which < 0 || which >= tracks.length || tracks[which] == null) return;
                try {
                    AudioTrack track = tracks[which];
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.stop();
                    track.reloadStaticData();
                    track.setPlaybackHeadPosition(0);
                    float boundedPitch = Math.max(0.75f, Math.min(which==5?2f:1.45f, pitch));
                    track.setPlaybackRate((int) (SAMPLE_RATE * boundedPitch));
                    track.play();
                    Log.d(TAG, "sound=" + which + " pitch=" + boundedPitch);
                } catch (Throwable error) {
                    Log.w(TAG, "sound failed " + which, error);
                }
            }

            void music(int beat) { int[] notes={0,4,7,4,9,7,4,2,0,4,7,12,9,7,4,2}; play(5,(float)Math.pow(2,notes[beat%16]/12.0)); } synchronized void pause() {
                generation++;
                for (AudioTrack track : tracks) {
                    if (track != null) {
                        try {
                            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.pause();
                        } catch (Throwable ignored) {
                            // Audio is a non-critical enhancement for this game.
                        }
                    }
                }
            }

            synchronized void release() {
                released=true;
                generation++;
                worker.shutdownNow();
                for (int i = 0; i < tracks.length; i++) {
                    if (tracks[i] != null) {
                        try {
                            tracks[i].release();
                        } catch (Throwable ignored) {
                            // Already released by a partially failed construction is harmless.
                        }
                        tracks[i] = null;
                    }
                }
            }


}

}
