/*
 * Copyright (C) 2017 Axel Müller <axel.mueller@avanux.de>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package de.avanux.smartapplianceenabler.appliance;

import org.joda.time.Interval;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@XmlAccessorType(XmlAccessType.FIELD)
public class Schedule {
    @XmlAttribute
    private String id;
    @XmlAttribute
    private int minRunningTime;
    @XmlAttribute
    private int maxRunningTime;
    @XmlElements({
            @XmlElement(name = "DayTimeframe", type = DayTimeframe.class),
            @XmlElement(name = "ConsecutiveDaysTimeframe", type = ConsecutiveDaysTimeframe.class)
    })
    private Timeframe timeframe;
    @XmlTransient
    DateTimeFormatter formatter = ISODateTimeFormat.basicTTimeNoMillis();
    /**
     * The number of seconds to be added to the maximum running time of the schedule during search for the next sufficient timeframe interval.
     * This additional time is available to the Sunny Home Manager for processing while still being able to fit running time into the timeframe.
     */
    @XmlTransient
    private static int additionalRunningTime = 900;


    public Schedule() {
    }

    public Schedule(int minRunningTime, int maxRunningTime, TimeOfDay earliestStart, TimeOfDay latestEnd) {
        this(minRunningTime, maxRunningTime, earliestStart, latestEnd, null);
    }

    public Schedule(int minRunningTime, int maxRunningTime, TimeOfDay earliestStart, TimeOfDay latestEnd, List<Integer> daysOfWeekValues) {
        this.minRunningTime = minRunningTime;
        this.maxRunningTime = maxRunningTime;
        this.timeframe = new DayTimeframe(earliestStart, latestEnd, daysOfWeekValues);
    }

    public String getId() {
        return id;
    }

    public static void setAdditionalRunningTime(int additionalRunningTime) {
        Schedule.additionalRunningTime = additionalRunningTime;
        LoggerFactory.getLogger(Schedule.class).debug("additional running time set to " + additionalRunningTime);
    }

    public int getMinRunningTime() {
        return minRunningTime;
    }

    public int getMaxRunningTime() {
        return maxRunningTime;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    /**
     * Returns the current or next timeframe if the remaining time is greater than maximum running time; otherwise the next timeframe is returned.
     * @param now the time reference
     * @param schedules the list of timeframes to choose from (current timeframe has to be first)
     * @param onlyAlreadyStarted consider only timeframe intervals already started
     * @param onlySufficient if true consider timeframe already started only if time to interval end exceeds min running time
     * @return the next timeframe becoming valid or null
     */
    public static TimeframeInterval getCurrentOrNextTimeframeInterval(LocalDateTime now, List<Schedule> schedules, boolean onlyAlreadyStarted, boolean onlySufficient) {
        if(schedules == null || schedules.size() == 0) {
            return null;
        }
        Map<Long,TimeframeInterval> startDelayOfTimeframeInterval = new TreeMap<>();
        for(Schedule schedule : schedules) {
            Timeframe timeframe = schedule.getTimeframe();
            timeframe.setSchedule(schedule);
            List<TimeframeInterval> timeframeIntervals = timeframe.getIntervals(now);
            for(TimeframeInterval timeframeInterval : timeframeIntervals) {
                Interval interval = timeframeInterval.getInterval();
                if(interval.contains(now.toDateTime())) {
                    // interval already started ...
                    if(onlySufficient) {
                        if(now.plusSeconds(schedule.getMaxRunningTime())
                                .plusSeconds(additionalRunningTime)
                                .isBefore(new LocalDateTime(interval.getEnd()))) {
                            // ... with remaining running time sufficient
                            return timeframeInterval;
                        }
                    }
                    else {
                        return timeframeInterval;
                    }
                }
                else if (! onlyAlreadyStarted) {
                    // interval starts in future
                    startDelayOfTimeframeInterval.put(interval.getStartMillis() - now.toDateTime().getMillis(), timeframeInterval);
                }
            }
        }
        if(startDelayOfTimeframeInterval.size() > 0) {
            Long startDelay = startDelayOfTimeframeInterval.keySet().iterator().next();
            return startDelayOfTimeframeInterval.get(startDelay);
        }
        return null;
    }

    /**
     * Returns schedules starting within the given interval.
     * @param considerationInterval
     * @return a (possibly empty) list of timeframes
     */
    public static List<TimeframeInterval> findTimeframeIntervals(LocalDateTime now, Interval considerationInterval, List<Schedule> schedules) {
        List<TimeframeInterval> matchingTimeframeIntervals = new ArrayList<>();
        if(schedules != null) {
            for(Schedule schedule : schedules) {
                Timeframe timeframe = schedule.getTimeframe();
                List<TimeframeInterval> timeframeIntervals = timeframe.getIntervals(now);
                for (TimeframeInterval timeframeInterval : timeframeIntervals) {
                    if(considerationInterval.contains(timeframeInterval.getInterval().getStart())) {
                        matchingTimeframeIntervals.add(timeframeInterval);
                    }
                }
            }
        }
        return matchingTimeframeIntervals;
    }

    @Override
    public String toString() {
        String text = "";

        if(timeframe != null) {
            text += timeframe.toString();
            text += "/";
        }

        text += minRunningTime + "s/" + maxRunningTime + "s";
        return text;
    }
}
