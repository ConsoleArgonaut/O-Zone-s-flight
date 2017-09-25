package onebit.o_zonesflight;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.GeomagneticField;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The Main Activity.
 * This Activity provides the user with an interface.
 * Depending on the state the UI will change.
 */
public class UIController extends Activity {
    private SensorManager Sensor;

    /** The reference to the Game. */
    private Game GameInstance;
    /** The state of the UI Controller */
    private UIControllerState State;


    // Timer
    private Timer GameTimer;
    private TimerTask TickWrapper;


    //Sprites
    private Bitmap oZone;
    private Bitmap meteor;

    //Resources
    private MediaPlayer player = null;
    private Bitmap display;
    private Canvas canvas;
    private ImageView canvasContainer;
    private OrientationSensorListener OrientationSensor;
    private float[] Gravity;
    private float[] Magnetic_Field;
    TextView scoreDisplay;
    /**
     * Called upon creation of the Activity. A.K.A. Application start/resume
     * The initialisation of the UI happens here
     * @param savedInstanceState May be used to load data from previous sessions
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //load unmanaged stuff
        super.onCreate(savedInstanceState);

        //Init sensor variables
        Gravity = new float[3];
        Magnetic_Field = new float[3];

        //Initialize sensor
        Sensor = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        OrientationSensor = new OrientationSensorListener(this, Sensor);


        //Initialize SaveFileManager
        String saveFile = getResources().getString(R.string.txt_defaultsavefile);
        ISaveFileManager saveFileManager = new LocalSaveFileManager(this);

        //Initialize Game & Load Data
        GameInstance = new Game(saveFileManager);

        //Initialize Sound
        InitializeMusic();

        //Initialize Sprites
        oZone = BitmapFactory.decodeResource(getResources(), R.mipmap.ozone_character);
        meteor = BitmapFactory.decodeResource(getResources(), R.mipmap.meteor);

        // Switch to GameMenu State
        GameMenu();
    }

    /**
     * Redirects onclick events to their respective target methods.
     * This is an unplanned workaround as onclick target methods require a view parameter.
     * @param sender The button that was 'clicked'
     */
    public void OnClickHandler(View sender) {
        System.out.print("TEST");
        switch (sender.getId()){
            case R.id.btn_start:    GameStart();    break;
            case R.id.btn_credits:  GameCredits();  break;
            case R.id.btn_back:     GameMenu();     break;
            // Invalid IDs should throw an exception
            default:
                Exception ex = new Exception("The ID of the previously clicked button is unknown.");
                ex.printStackTrace();
                //TODO; maybe implement error handler or something
                break;
        }
    }
    /**
     * Makes the game progress while it's running.
     * Called by the GameTimer each tick.
     */
    protected void GameTick(){

        //Clear Screen
        canvas.drawColor(Color.BLACK);
        //Redraw

        //Player
        //Calculate position
        PointF pos = TranslatePlayerPos(GameInstance.GetPlayer(), display);
        //Draw Player
        canvas.drawBitmap(
                oZone,
                new Rect(
                        0,
                        0,
                        oZone.getWidth(),
                        oZone.getHeight()),
                new RectF(
                        pos.x,
                        pos.y,
                        pos.x + display.getWidth() * (Settings.Player_Width / (float)Settings.Environment_Width),
                        pos.y + display.getHeight() * (Settings.Player_Height / (float)Settings.Environment_Height)),
                null);
        //Iterate meteorites
        for (Meteorite m: GameInstance.GetMeteorites()) {
            //Calculate
            pos = TranslateMeteorPos(m, display);
            canvas.drawBitmap(
                    meteor,
                    new Rect(
                            0,
                            0,
                            meteor.getWidth(),
                            meteor.getHeight()),
                    new RectF(
                            pos.x,
                            pos.y,
                            pos.x + display.getWidth() / Settings.Environment_LineCount,
                            pos.y + display.getWidth() / Settings.Environment_LineCount/* + display.getHeight() * (Settings.Player_Height / (float)Settings.Environment_Height)*/),
                    null);
        }
        // MAke screen refresh
        canvasContainer.invalidate();

        // Read Sensor Data
        float[] Rota = new float[9];
        float[] I = new float[9];
        float[] values = new float[3];
        Sensor.getRotationMatrix(Rota,I,Gravity, Magnetic_Field);
        Sensor.getOrientation(Rota,values);

        // Format sensor data
        float azi = values[0] / (float)Math.PI;
        float pitch = 2 * values[1] / (float)Math.PI;
        float roll = values[2] / (float)Math.PI;

        //Do Game Tick
        boolean result = GameInstance.DoFrame(roll);
        if (!result)
        {
            TickWrapper.cancel();
            GameTimer.cancel();
            GameOver();
        } else{
            // Update Score
            scoreDisplay.setText(String.valueOf(GameInstance.GetScore()));
        }
    }

    /**
     * Initiates the Game
     */
    public void GameStart(){
        // Set layout and state
        setContentView(R.layout.lay_gamerunning);
        State = UIControllerState.Running;

        //Reset existing game status
        GameInstance.ResetGame();
        scoreDisplay = (TextView)findViewById(R.id.tex_score);
        //
        canvasContainer = (ImageView) findViewById(R.id.img_canvas);
        canvasContainer.getViewTreeObserver().addOnGlobalLayoutListener(new CanvasLayoutListener(this, canvasContainer));
        // Continue rest once the canvas is available on the LayoutDoneCallback
    }

    /**
     * Layout has been finished, the game screen size calculated. Game Loop can now be started
     * @param display The bitmap representing the display
     */
    public void LayoutDoneCallback(Bitmap display){
        this.display = display;
        final Handler mainHandler = new Handler(getMainLooper());
        canvas = new Canvas(display);
        GameTimer = new Timer();
        TickWrapper = new TimerTask() {
            @Override
            public void run() {
                mainHandler.post(new GameTickRunnable(UIController.this));
            }
        };

        GameTimer.schedule(TickWrapper, 0, Settings.Gameplay_MillisecondsPerFrame);
    }


    public void GameOver(){
        // Set layout and state

        setContentView(R.layout.lay_gameover);
        State = UIControllerState.GameOver;

        //load highscore
        TextView highScoreText = (TextView) findViewById(R.id.tex_highscore);
        highScoreText.setText(
                String.format(
                        Locale.getDefault(),
                        "%s: %d",
                        getResources().getText(R.string.txt_highscore),
                        GameInstance.GetHighScore()));

        //load score

        TextView scoreText = (TextView) findViewById(R.id.tex_score);
        scoreText.setText(
                String.format(
                        Locale.getDefault(),
                        "%s: %d",
                        getResources().getText(R.string.txt_score),
                        GameInstance.GetScore()));
    }

    public void GameCredits(){
        setContentView(R.layout.lay_gamecredits);
        State = UIControllerState.Credits;
    }

    /**
     * Sets the UIManage into the GameMenu state and applies changes accordingly
     */
    public void GameMenu(){
        // Set layout and state
        setContentView(R.layout.lay_gamestart);
        State = UIControllerState.MainMenu;

        //load highscore
        TextView highScoreText = (TextView) findViewById(R.id.tex_highscore);
        highScoreText.setText(
                String.format(
                        Locale.getDefault(),
                        "%s: %d",
                        getResources().getText(R.string.txt_highscore),
                        GameInstance.GetHighScore()));
    }


    public void setGravity(float[] values){ Gravity = values; }
    public void setMagField(float[] values){ Magnetic_Field = values; }

    public void InitializeMusic(){
        player = MediaPlayer.create(this, R.raw.apocalypse);
        player.setLooping(true); // Set looping
        player.setVolume(1.0f, 1.0f);
        player.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.pause();
    }

    /**
     *
     */
    @Override
    protected void onResume() {
        super.onResume();
        player.start();
    }

    /**
     * Helper method to translate the player position of the game to corresponding display coordinates
     * @param player The player instance
     * @param display The display bitmap to render the player on
     * @return A calculated PointF for the canvas to draw the player at
     */
    private PointF TranslatePlayerPos(Player player, Bitmap display){
        return new PointF(
                player.GetPosition() / (Settings.Environment_Width + Settings.Player_Width) * display.getWidth(),
                display.getHeight() - display.getHeight() * Settings.Player_Height / (float)Settings.Environment_Height);
    }

    /**
     * Helper method that translates the Game Position to a corresponding position on display.
     * @param m The Meteorite instance
     * @param display The display bitmap to render the meteorite on
     * @return The calculated position for the canvas to draw the meteorite
     */
    private PointF TranslateMeteorPos(Meteorite m, Bitmap display){
        return new PointF(
                (m.GetCourse()-1) * Settings.Environment_LineWidth / (float)Settings.Environment_Width * display.getWidth(),
                (Settings.Environment_Height - m.GetLatitude() + Settings.Meteorites_Height - Settings.Player_Height) / Settings.Environment_Height * display.getHeight()
        );
    }
}
