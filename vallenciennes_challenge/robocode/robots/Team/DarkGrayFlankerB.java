package Team;

import java.awt.*;

public class DarkGrayFlankerB extends DarkGrayTeamBase {

    protected boolean isLeaderBot() { return false; }
    protected double preferredDistance() { return 250.0; }
    protected double roleOffsetAngle() { return -0.75; }
    protected double fireCap() { return 2.5; }
    protected double aggression() { return 0.78; }

    protected Color bodyColor() { return new Color(85, 85, 85); }
    protected Color gunColor() { return Color.BLACK; }
    protected Color radarColor() { return new Color(125, 125, 125); }
    protected Color bulletColor() { return Color.YELLOW; }
    protected Color scanColor() { return Color.WHITE; }
}