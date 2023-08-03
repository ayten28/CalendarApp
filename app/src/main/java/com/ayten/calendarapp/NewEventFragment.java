package com.ayten.calendarapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.microsoft.graph.models.DateTimeTimeZone;
import com.microsoft.graph.models.Event;
import com.microsoft.graph.models.ScheduleInformation;
import com.microsoft.graph.requests.CalendarGetScheduleCollectionPage;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class NewEventFragment extends Fragment {
    private static final String TIME_ZONE = "timeZone";

    private String mTimeZone;
    private TextInputLayout mSubject;
    private TextInputLayout mAttendees;
    private TextInputLayout mStartInputLayout;
    private TextInputLayout mEndInputLayout;
    private TextInputLayout mBody;
    private EditTextDateTimePicker mStartPicker;
    private EditTextDateTimePicker mEndPicker;

    private Duration duration;
    private int interval;
    private List<Event> mEventList = null;
    private List<ScheduleInformation> mSchedule = null;
    private NavigationView mNavigationView;
    private static final String USER_NAME = "userName";
    private static final String USER_EMAIL = "userEmail";
    private String mUserEmail;

    private String mUserName;

    public NewEventFragment() {}

    public static NewEventFragment createInstance(String timeZone,String userName, String userEmail) {
        NewEventFragment fragment = new NewEventFragment();

        // Add the provided time zone to the fragment's arguments
        Bundle args = new Bundle();
        args.putString(TIME_ZONE, timeZone);
        args.putString(USER_NAME, userName);
        args.putString(USER_EMAIL, userEmail);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mTimeZone = getArguments().getString(TIME_ZONE);
            mUserName = getArguments().getString(USER_NAME);
            mUserEmail = getArguments().getString(USER_EMAIL);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View newEventView = inflater.inflate(R.layout.fragment_new_event, container, false);
        View homeView = inflater.inflate(R.layout.main_home, container, false);


        ZoneId userTimeZone = GraphToIana.getZoneIdFromWindows(mTimeZone);

        mSubject = newEventView.findViewById(R.id.neweventsubject);
        mAttendees = newEventView.findViewById(R.id.neweventattendees);
        mBody = newEventView.findViewById(R.id.neweventbody);

        mStartInputLayout = newEventView.findViewById(R.id.neweventstartdatetime);
        mStartPicker = new EditTextDateTimePicker(getContext(),
                mStartInputLayout.getEditText(),
                userTimeZone);




        mEndInputLayout = newEventView.findViewById(R.id.neweventenddatetime);
        mEndPicker = new EditTextDateTimePicker(getContext(),
                mEndInputLayout.getEditText(),
                userTimeZone);

        Button createButton = newEventView.findViewById(R.id.createevent);
        createButton.setOnClickListener(v -> {
            // Clear any errors
            mSubject.setErrorEnabled(false);
            mEndInputLayout.setErrorEnabled(false);

            showProgressBar();

            createEvent();
        });

        return newEventView;
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

    private void createEvent() {
        String subject = mSubject.getEditText().getText().toString();
        String attendees = mAttendees.getEditText().getText().toString();
        String body = mBody.getEditText().getText().toString();

        ZonedDateTime startDateTime = mStartPicker.getZonedDateTime();
        ZonedDateTime endDateTime = mEndPicker.getZonedDateTime();

        // Validate
        boolean isValid = true;
        // Subject is required
        if (subject.isEmpty()) {
            isValid = false;
            mSubject.setError("You must set a subject");
        }

        // End must be after start
        if (!endDateTime.isAfter(startDateTime)) {
            isValid = false;
            mEndInputLayout.setError("The end must be after the start");
        }

        duration = Duration.between(startDateTime,endDateTime);
        interval = Math.toIntExact(duration.toMinutes());

        if(interval < 5)
            isValid = false;

        if (isValid)
        {
            // Split the attendees string into an array
            String[] attendeeArray = attendees.split(";");
            GraphHelper.getInstance().getSchedule(startDateTime,
                    endDateTime,interval,mUserEmail,
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
        }
        else
        {
            hideProgressBar();
            Snackbar.make(getView(),
                    "Make sure that all the fields are filled",
                    BaseTransientBottomBar.LENGTH_SHORT).show();
        }
    }

    private void getSched() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                List<String> sItem = mSchedule.stream().map(x -> x.availabilityView).collect(Collectors.toList());
                String t = sItem.get(0);
                if (t.equals("0")) {
                    String subject = mSubject.getEditText().getText().toString();
                    String attendees = mAttendees.getEditText().getText().toString();
                    String[] attendeeArray = attendees.split(";");
                    String body = mBody.getEditText().getText().toString();

                    ZonedDateTime startDateTime = mStartPicker.getZonedDateTime();
                    ZonedDateTime endDateTime = mEndPicker.getZonedDateTime();
                    GraphHelper.getInstance()
                            .createEvent(subject,
                                    startDateTime,
                                    endDateTime,
                                    mTimeZone,
                                    attendeeArray,
                                    body)
                            .thenAccept(newEvent -> {
                                hideProgressBar();
                                Snackbar.make(getView(),
                                        "Event created",
                                        BaseTransientBottomBar.LENGTH_SHORT).show();
                                MainHome fragment = MainHome.createInstance(mUserName,mTimeZone,mUserEmail);
                                getFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, fragment)
                                        .commit();

                            })
                            .exceptionally(exception -> {
                                hideProgressBar();
                                Log.e("GRAPH", "Error creating event", exception);
                                Snackbar.make(getView(),
                                        exception.getMessage(),
                                        BaseTransientBottomBar.LENGTH_LONG).show();
                                return null;
                            });
                }
                if (t.equals("2")) {
                    Snackbar.make(getView(),
                            "This time is busy!",
                            BaseTransientBottomBar.LENGTH_SHORT).show();
                    hideProgressBar();
                }
     }
        });
    }




}