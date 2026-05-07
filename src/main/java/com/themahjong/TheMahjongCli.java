package com.themahjong;

public final class TheMahjongCli {

    private TheMahjongCli() {
    }

    public static void main(String[] args) {
        TheMahjongMatch d = TheMahjongMatch.defaults();
        int playerCount = parseArg(args, 0, d.playerCount());
        int startingPoints = parseArg(args, 1, d.startingPoints());
        int targetPoints = parseArg(args, 2, d.targetPoints());
        int roundCount = parseArg(args, 3, d.roundCount());

        TheMahjongMatch createdMatch = TheMahjongMatch.defaults()
                .withPlayerCount(playerCount)
                .withStartingPoints(startingPoints)
                .withTargetPoints(targetPoints)
                .withRoundCount(roundCount)
                .validate();
        String createdOutput = renderMatch(createdMatch);
        TheMahjongMatch startedMatch = createdMatch.startRound();

        System.out.println(createdOutput);
        System.out.println();
        System.out.println(renderMatch(startedMatch));
    }

    static String renderMatch(TheMahjongMatch match) {
        StringBuilder builder = new StringBuilder();
        builder.append("Match state: ").append(match.state()).append('\n');
        builder.append("Players: ").append(match.playerCount()).append('\n');
        builder.append("Starting points: ").append(match.startingPoints()).append('\n');
        builder.append("Target points: ").append(match.targetPoints()).append('\n');
        builder.append("Round count: ").append(match.roundCount()).append('\n');
        builder.append("Completed rounds: ").append(match.completedRounds().size()).append('\n');

        if (match.currentRound().isEmpty()) {
            builder.append("Current round: none");
        } else {
            TheMahjongRound round = match.currentRound().orElseThrow();
            builder.append("Current round state: ").append(round.state()).append('\n');
            builder.append("Round wind: ").append(round.roundWind()).append('\n');
            builder.append("Hand number: ").append(round.handNumber()).append('\n');
            builder.append("Dealer seat: ").append(round.dealerSeat()).append('\n');
            builder.append("Current turn seat: ").append(round.currentTurnSeat()).append('\n');
            builder.append("Player points: ");
            for (int seat = 0; seat < round.players().size(); seat++) {
                if (seat > 0) {
                    builder.append(", ");
                }
                builder.append("seat ").append(seat).append('=').append(round.players().get(seat).points());
            }
        }
        return builder.toString();
    }

    private static int parseArg(String[] args, int index, int defaultValue) {
        if (index >= args.length) {
            return defaultValue;
        }
        return Integer.parseInt(args[index]);
    }
}
