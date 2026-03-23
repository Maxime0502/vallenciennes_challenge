package sample;

import robocode.AdvancedRobot;

import java.awt.*;

public class DarkGrayBot extends AdvancedRobot {
    boolean movingForward;

    public void run(){
        setBodyColor(new Color(95, 92, 92));
        setGunColor(new Color(95, 92, 92));
        setRadarColor(new Color(95, 92, 92));
        setBulletColor(new Color(95, 92, 92));
        setScanColor(new Color(95, 92, 92));

        while(true){
            setAhead(40000);
        }
    }
}
