package mon.robot;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;

public class GreyDarkChallenge1 extends AdvancedRobot {
    
    private double moveDirection = 1;
    private double lastScanTime = 0;

    public void run() {
        // --- LOOK TOTAL DARK GREY ---
        Color darkGrey = new Color(35, 35, 35);
        setBodyColor(darkGrey);
        setGunColor(darkGrey);
        setRadarColor(darkGrey);
        setBulletColor(Color.LIGHT_GRAY);
        setScanColor(new Color(50, 50, 50));

        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);

        // Scan infini pour une conscience situationnelle totale
        while (true) {
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double absBearing = getHeadingRadians() + e.getBearingRadians();
        double distance = e.getDistance();
        
        // --- STRATÉGIE 1 : FUITE ET DISTANCIATION ---
        // Dans une mêlée à 9, la survie = distance.
        // On essaie de maintenir un angle de 100-110 degrés pour s'éloigner en tournant.
        double turnAngle = e.getBearingRadians() + Math.PI / 2 + (distance < 400 ? 0.35 : 0.15);
        setTurnRightRadians(Utils.normalRelativeAngle(turnAngle));
        
        // On avance par petits bonds pour rester imprévisible
        if (getTime() % 20 == 0) {
            moveDirection *= (Math.random() > 0.5 ? 1 : -1);
        }
        setAhead(150 * moveDirection);

        // --- STRATÉGIE 2 : TIR SÉLECTIF (L'ÉCONOMIE) ---
        // Ne pas gaspiller d'énergie si la cible est trop loin dans le chaos
        if (distance < 500 || getEnergy() > 60) {
            double firePower = Math.min(Math.min(3.0, 600 / distance), e.getEnergy() / 4);
            
            // Anticipation simplifiée pour la mêlée
            double bulletSpeed = 20 - 3 * firePower;
            double predict = (e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing) / bulletSpeed);
            
            setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + predict));

            if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 5) {
                setFire(firePower);
            }
        }

        // --- STRATÉGIE 3 : RADAR "LOCK-ON-PASS" ---
        // On force le radar à faire un demi-tour rapide s'il détecte quelqu'un 
        // pour garder l'info fraîche sans s'arrêter de scanner les autres.
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 1.5);
    }

    // Gestion intelligente des murs pour le 1000x1000
    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
        setAhead(150 * moveDirection);
    }

    // Si on se fait percuter à 9 robots, il faut s'éjecter vite !
    public void onHitRobot(HitRobotEvent e) {
        moveDirection = -moveDirection;
        setAhead(100 * moveDirection);
    }

    // Si on gagne un round, petite danse de victoire (optionnel)
    public void onWin(WinEvent e) {
        for (int i = 0; i < 50; i++) {
            turnRight(30);
            turnLeft(30);
        }
    }
}