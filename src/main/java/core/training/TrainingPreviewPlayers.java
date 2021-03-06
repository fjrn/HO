package core.training;

import core.db.DBManager;
import core.gui.RefreshManager;
import core.gui.Refreshable;
import core.model.HOVerwaltung;
import core.model.match.MatchKurzInfo;
import core.model.match.MatchStatistics;
import core.model.match.MatchType;
import core.model.player.IMatchRoleID;
import core.model.player.MatchRoleID;
import core.model.player.Player;
import module.lineup.LineupPosition;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

/**
 *
 * Training preview of players for the week
 *
 * @author yaute
 */


public class TrainingPreviewPlayers implements Refreshable {

    private static TrainingPreviewPlayers m_clInstance;

    private HashMap<Player, TrainingPreviewPlayer> players = new HashMap<>();
    private int nextWeekTraining = -1;
    private boolean isFuturMatchInit =false;
    private WeeklyTrainingType weekTrainTyp = null;
    private List<MatchStatistics> lMatchStats = null;
    private List<LineupPosition> lLinueupPos = null;

    //~ Constructors -------------------------------------------------------------------------------

    /**
     * Create TrainingPreviewPlayers object
     * Add to refresh
     */
    public TrainingPreviewPlayers() {
        RefreshManager.instance().registerRefreshable(this);
    }

    //~ Methods ------------------------------------------------------------------------------------

    /**
     * Returns a singleton TrainingPreviewPlayers object
     *
     * @return instance of TrainingPreviewPlayers
     */
    public static TrainingPreviewPlayers instance() {
        if (m_clInstance == null) {
            m_clInstance = new TrainingPreviewPlayers();
        }
        return m_clInstance;
    }

    /**
     * get training preview of a player
     *
     * @param player Player
     * @return TrainingPreviewPlayer
     */
    public TrainingPreviewPlayer getTrainPreviewPlayer(Player player) {

        if (players.get(player) == null) {
            calculateWeeklyTrainingForPlayer(player);
        }
        return players.get(player);
    }

    /**
     * reinit object and clean database
     */
    public void reInit() {
        refresh();
        DBManager.instance().removeMatchOrder();
    }

    /**
     * refresh object
     */
    public void refresh() {
        if (players != null)
            players.clear();
        if (lMatchStats != null)
            lMatchStats.clear();
        if (lLinueupPos != null)
            lLinueupPos.clear();
        nextWeekTraining = -1;
        weekTrainTyp = null;
        isFuturMatchInit = false;
    }

    /**
     * get next training
     *
     * @return     training id
     */
    public int getNextWeekTraining() {

        if (nextWeekTraining == -1) {
            int nextWeekSaison = HOVerwaltung.instance().getModel().getBasics().getSeason();
            int nextWeekWeek = HOVerwaltung.instance().getModel().getBasics().getSpieltag();

            if (nextWeekWeek == 16) {
                nextWeekWeek = 1;
                nextWeekSaison++;
            } else {
                nextWeekWeek++;
            }
            nextWeekTraining = DBManager.instance().getFuturTraining(nextWeekSaison, nextWeekWeek);
        }

        return nextWeekTraining;
    }

    /**
     * calculate training preview of a player
     *
     * @param player:   player
     */
    private void calculateWeeklyTrainingForPlayer(Player player) {

        final int playerID = player.getSpielerID();
        int fullTrain = 0;
        int partialTrain = 0;
        boolean fullFuturTrain = false;
        boolean partialFuturTrain = false;
        int iStamina = 0;
        boolean bEstimedStamina = false;

        getMatchesForTraining();

        //for (int i = 0; i < lMatchStats.size(); i++) {
        for ( var ms : lMatchStats){

            if (weekTrainTyp.getPrimaryTrainingSkillPositions() != null) {
                fullTrain += ms.getTrainMinutesPlayedInPositions(playerID, weekTrainTyp.getPrimaryTrainingSkillPositions());
                if (fullTrain > 90)
                    fullTrain = 90;
            }
            if (weekTrainTyp.getPrimaryTrainingSkillSecondaryTrainingPositions() != null) {
                partialTrain += ms.getTrainMinutesPlayedInPositions(playerID, weekTrainTyp.getPrimaryTrainingSkillSecondaryTrainingPositions());
                if (partialTrain > 90)
                    partialTrain = 90;
            }
            // If player receive training, don't display stamina icon
            if (fullTrain == 0 && partialTrain == 0) {
                iStamina += ms.getStaminaMinutesPlayedInPositions(playerID);
                if (iStamina > 90)
                    iStamina = 90;
            }
        }

        //for (int i = 0; i < lLinueupPos.size(); i++) {
        for ( var pos: lLinueupPos ){
            MatchRoleID roleId = pos.getPositionBySpielerId(playerID);

            if (roleId != null) {
                if (weekTrainTyp.getPrimaryTrainingSkillPositions() != null) {
                    for (int k = 0; k < weekTrainTyp.getPrimaryTrainingSkillPositions().length; k++) {
                        if (roleId.getId() == weekTrainTyp.getPrimaryTrainingSkillPositions()[k]) {
                            fullFuturTrain = true;
                            break;
                        }
                    }
                }
                if (!fullFuturTrain && weekTrainTyp.getPrimaryTrainingSkillSecondaryTrainingPositions() != null) {
                    for (int k = 0; k < weekTrainTyp.getPrimaryTrainingSkillSecondaryTrainingPositions().length; k++) {
                        if (roleId.getId() == weekTrainTyp.getPrimaryTrainingSkillSecondaryTrainingPositions()[k]) {
                            partialFuturTrain = true;
                            break;
                        }
                    }
                }
                // If player receive training, don't display stamina icon
                if (fullTrain == 0 && partialTrain == 0 && !fullFuturTrain && !partialFuturTrain && 
                        roleId.getId() < IMatchRoleID.substGK1) {
                    bEstimedStamina = true;
                }
            }
        }

        players.put(player,new TrainingPreviewPlayer(fullTrain, partialTrain, 
                fullFuturTrain, partialFuturTrain,
                iStamina, bEstimedStamina));
    }

    /**
     * get the matchs concerning by the training week
     */
    private void getMatchesForTraining() {

        if (!isFuturMatchInit) {
            var lastTraining = TrainingManager.instance().getLastTrainingWeek();

            lMatchStats = new ArrayList<>();
            lLinueupPos = new ArrayList<>();
            isFuturMatchInit = true;

            if (lastTraining != null) {
                weekTrainTyp = WeeklyTrainingType.instance(lastTraining.getTrainingType());
                for (var matchInfo : lastTraining.getMatches()) {
                    if (matchInfo.getMatchStatus() == MatchKurzInfo.FINISHED) {
                        //Get the MatchLineup by id
                        //MatchLineupTeam mlt = DBManager.instance().getMatchLineupTeam(matchInfo.getMatchID(), MatchKurzInfo.user_team_id);
                        var mlt = matchInfo.getMatchdetails().getTeamLineup();
                        lMatchStats.add(new MatchStatistics(matchInfo, mlt));
                    } else if (matchInfo.getMatchStatus() == MatchKurzInfo.UPCOMING) {
                        LineupPosition lineuppos = DBManager.instance().getMatchOrder(matchInfo.getMatchID(), matchInfo.getMatchTyp());
                        if (lineuppos != null)
                            lLinueupPos.add(lineuppos);
                    }
                }
            }
        }
    }
}
