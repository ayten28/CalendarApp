package com.ayten.calendarapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.DateTimeTimeZone;
import com.microsoft.graph.models.Event;
import com.microsoft.graph.models.ScheduleInformation;
import com.microsoft.graph.models.ScheduleItem;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.exception.MsalException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class MainHome extends Fragment {
    private static final String USER_NAME = "userName";

    private String mUserName;
    private String mUserEmail;
    private static final String TIME_ZONE = "timeZone";
    private static final String USER_EMAIL = "userEmail";
    private List<Event> mEventList = null;
    private List<ScheduleInformation> mSchedule = null;
    private String mTimeZone;
    private List<DateTimeTimeZone> startdateList;
    private Duration duration;
    private int interval;
    private String state = "0";



    public MainHome() {
    }

    public static MainHome createInstance(String userName, String timeZone, String userEmail) {
        MainHome fragment = new MainHome();

        // Add the provided username to the fragment's arguments
        Bundle args = new Bundle();
        args.putString(USER_NAME, userName);
        args.putString(TIME_ZONE, timeZone);
        args.putString(USER_EMAIL, userEmail);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mUserName = getArguments().getString(USER_NAME);
            mTimeZone = getArguments().getString(TIME_ZONE);
            mUserEmail = getArguments().getString(USER_EMAIL);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View homeView = inflater.inflate(R.layout.main_home, container, false);
        final GraphHelper graphHelper = GraphHelper.getInstance();

        ImageButton btnAdd = homeView.findViewById(R.id.createeventmain);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                NewEventFragment fragment = NewEventFragment.createInstance(mTimeZone, mUserName, mUserEmail);
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
            }
        });

        //ZoneId tzId = GraphToIana.getZoneIdFromWindows(mTimeZone);

        //----------------Timer ---------------
        Timer timer = new Timer();
        final long ONE_MINUTE_IN_MILLIS = 60000;
        Calendar date = Calendar.getInstance();
        long t = date.getTimeInMillis();
        Date afterAddingTenMins = new Date(t + (1 * ONE_MINUTE_IN_MILLIS));

        timer.schedule(callApi, 0, 60000);
        //-------------------------------------


        return homeView;
    }

    private void showProgressBar() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getActivity().findViewById(R.id.progressbar)
                        .setVisibility(View.VISIBLE);
                getActivity().findViewById(R.id.fragment_container)
                        .setVisibility(View.GONE);
            }
        });
    }

    private void hideProgressBar() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getActivity().findViewById(R.id.progressbar)
                        .setVisibility(View.GONE);
                getActivity().findViewById(R.id.fragment_container)
                        .setVisibility(View.VISIBLE);
            }
        });
    }

    private void addEventsToList() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ListView eventListView = getView().findViewById(R.id.eventlist);

                EventListAdapter listAdapter = new EventListAdapter(getActivity(),
                        R.layout.event_list_item, mEventList);
                eventListView.setAdapter(listAdapter);
                List<String> filtered = mEventList.stream().map(x -> x.subject).collect(Collectors.toList());
                startdateList = mEventList.stream().map(x -> x.start).collect(Collectors.toList());
                List<Integer> tst = mEventList.stream().map(x -> x.reminderMinutesBeforeStart).collect(Collectors.toList());
            }
        });
    }

    TimerTask callApi = new TimerTask() {
        @Override
        public void run() {
            final GraphHelper graphHelper = GraphHelper.getInstance();
            ZoneId tzId = GraphToIana.getZoneIdFromWindows(mTimeZone);
            ZonedDateTime currentTime = ZonedDateTime.now(tzId);
            ZonedDateTime start = currentTime;
            ZonedDateTime end = start.plusMinutes(5);

            if(state.equals("0"))
            {
                start = currentTime.minusMinutes(4);
                end = currentTime.plusMinutes(1);
            }
            graphHelper.getSchedule(start,
                    end,5,mUserEmail,
                    mTimeZone).thenApplyAsync(schedule -> {
                return mSchedule = schedule;
            }).thenAccept(schedule -> {
                getSched();
            })
                    .exceptionally(exception -> {
                        hideProgressBar();
                        Log.e("GRAPH", "Error getting time", exception);
                        Snackbar.make(getView(),
                                exception.getMessage(),
                                BaseTransientBottomBar.LENGTH_LONG).show();
                        return null;
                    });

            ZonedDateTime endDate = start.plusHours(24);
            graphHelper
                    .getCalendarView(start, endDate, mTimeZone)
                    .thenApplyAsync(eventList -> {
                        return mEventList = eventList;
                    })
                    .thenAccept(eventList -> {
                        addEventsToList();
                        hideProgressBar();
                    })
                    .exceptionally(exception -> {
                        hideProgressBar();
                        Log.e("GRAPH", "Error getting events", exception);
                        Snackbar.make(getView(),
                                exception.getMessage(),
                                BaseTransientBottomBar.LENGTH_LONG).show();
                        return null;
                    });
        }
    };

    private void getSched() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LinearLayout topColor = getView().findViewById(R.id.top);
                List<String> sItem = mSchedule.stream().map(x -> x.availabilityView).collect(Collectors.toList());
                String t = sItem.get(0);
                state = t;
                if (t.equals("0")) {
                    topColor.setBackgroundColor(getResources().getColor(R.color.noMeeting));//(Color.parseColor("#A2D5AB"));
                }
                if (t.equals("2")) {
                    topColor.setBackgroundColor(getResources().getColor(R.color.onMeeting));//(Color.parseColor("#FF5959"));
                }
            }
        });
    }

}