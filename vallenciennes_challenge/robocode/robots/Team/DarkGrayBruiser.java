package Team;

import java.awt.*;

public class DarkGrayBruiser extends DarkGrayTeamBase {

    protected boolean isLeaderBot() { return false; }
    protected double preferredDistance() { return 180.0; }
    protected double roleOffsetAngle() { return 0.0; }
    protected double fireCap() { return 3.0; }
    protected double aggression() { return 0.95; }

    protected Color bodyColor() { return new Color(35, 35, 35); }
    protected Color gunColor() { return Color.BLACK; }
    protected Color radarColor() { return new Color(100, 100, 100); }
    protected Color bulletColor() { return Color.RED; }
    protected Color scanColor() { return Color.WHITE; }
}