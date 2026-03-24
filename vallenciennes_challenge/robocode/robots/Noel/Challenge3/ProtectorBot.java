package Noel;

import java.awt.Color;

import robocode.*;
import robocode.util.Utils;

public class ProtectorBot extends TeamRobot {

    private String targetName = null;
    private int direction = 1;

    public void run() {
    	
    	setBodyColor(Color.DARK_GRAY);
		setGunColor(Color.DARK_GRAY);
		setRadarColor(Color.DARK_GRAY);
		setBulletColor(Color.DARK_GRAY);
		setScanColor(Color.DARK_GRAY);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            setTurnRadarRight(360);
            setAhead(70 * direction);
            setTurnRight(20 * direction);
            execute();
        }
    }

    public void onMessageReceived(MessageEvent e) {
        Object msg = e.getMessage();
        if (msg instanceof String) {
            targetName = (String) msg;
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) return;

        double absBearing = getHeadingRadians() + e.getBearingRadians();

        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 2);

        if (targetName == null || e.getName().equals(targetName) || e.getDistance() < 220) {
            setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians() + Math.PI / 2 - 0.5 * direction));
            setAhead(140 * direction);

            double gunTurn = Utils.normalRelativeAngle(absBearing - getGunHeadingRadians());
            setTurnGunRightRadians(gunTurn);

            if (getGunHeat() == 0) {
                setFire(2.0);
            }
        }

        if (Math.random() < 0.10) {
            direction = -direction;
        }

        execute();
    }

    public void onHitWall(HitWallEvent e) {
        direction = -direction;
        setBack(100);
        setTurnRight(90);
        execute();
    }
}