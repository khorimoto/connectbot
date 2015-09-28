/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot;

import org.connectbot.bean.HostBean;
import org.connectbot.util.HostDatabase;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;

public class HostDisplayEditorFragment extends Fragment {

	private static final String TAG = "HostDisplayEditorFragment";
	private static final int DEFAULT_FONT_SIZE = 10;

	// Listener for host display changes.
	private Listener mListener;

	// The display data currently present in the widget.
	private DisplayData mDisplayData;

	// Values for the colors displayed in the color Spinner. These are not necessarily the same as
	// the text in the Spinner because the text is localized while these values are not.
	private TypedArray mColorValues;

	private EditText mNicknameField;
	private Spinner mColorSelector;
	private EditText mFontSizeField;

	public HostDisplayEditorFragment() {}

	/**
	 * Creates a HostDisplayEditorFragment. This static function should be used instead of calling
	 * the constructor directly.
	 * @param existingHost The existing host if one exists, or null if a new host is being created.
	 */
	public static HostDisplayEditorFragment createInstance(HostBean existingHost) {
		HostDisplayEditorFragment fragment = new HostDisplayEditorFragment();

		Bundle args = new Bundle();
		if (existingHost != null) {
			args.putString(HostDatabase.FIELD_HOST_NICKNAME, existingHost.getHostname());
			args.putString(HostDatabase.FIELD_HOST_COLOR, existingHost.getColor());
			args.putInt(HostDatabase.FIELD_HOST_FONTSIZE, existingHost.getFontSize());
		}
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle bundle = savedInstanceState == null ? getArguments() : savedInstanceState;

		mDisplayData = new DisplayData();
		mDisplayData.nickname = bundle.getString(HostDatabase.FIELD_HOST_NICKNAME);
		mDisplayData.color = bundle.getString(HostDatabase.FIELD_HOST_COLOR);
		mDisplayData.fontSize = bundle.getInt(HostDatabase.FIELD_HOST_FONTSIZE);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_host_display_editor, container, false);

		mNicknameField = (EditText) view.findViewById(R.id.nickname_field);
		mNicknameField.setText(mDisplayData.nickname);
		mNicknameField.addTextChangedListener(
				new DisplayDataUpdater(HostDatabase.FIELD_HOST_NICKNAME));

		mColorSelector = (Spinner) view.findViewById(R.id.color_selector);
		if (mDisplayData.color != null) {
			// Unfortunately, TypedArray doesn't have an indexOf(String) function, so search through
			// the array for the saved color.
			for (int i = 0; i < mColorValues.getIndexCount(); i++) {
				if (mDisplayData.color.equals(mColorValues.getString(i))) {
					mColorSelector.setSelection(i);
					break;
				}
			}
		}
		mColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				mDisplayData.color = mColorValues.getString(position);
				mListener.onDisplaySettingsChanged(mDisplayData);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		mFontSizeField = (EditText) view.findViewById(R.id.font_size_field);
		mFontSizeField.setText(Integer.toString(
				mDisplayData.fontSize > 0 ? mDisplayData.fontSize : DEFAULT_FONT_SIZE));
		mFontSizeField.addTextChangedListener(
				new DisplayDataUpdater(HostDatabase.FIELD_HOST_FONTSIZE));

		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			mListener = (Listener) getParentFragment();
		} catch (ClassCastException e) {
			throw new ClassCastException(getParentFragment().toString()	+
					" must implement Listener");
		}

		// Now that the fragment is attached to an Activity, fetch the array from the attached
		// Activity's resources.
		mColorValues = getResources().obtainTypedArray(R.array.list_color_values);
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		savedInstanceState.putString(HostDatabase.FIELD_HOST_NICKNAME, mDisplayData.nickname);
		savedInstanceState.putString(HostDatabase.FIELD_HOST_COLOR, mDisplayData.color);
		savedInstanceState.putInt(HostDatabase.FIELD_HOST_FONTSIZE, mDisplayData.fontSize);
	}

	public static class DisplayData {
		public String nickname;
		public String color;
		public int fontSize;
	}

	/**
	 * Interface for listeners of display setting changes.
	 */
	public interface Listener {
		void onDisplaySettingsChanged(DisplayData data);
	}

	private class DisplayDataUpdater implements TextWatcher {

		private final String mFieldType;

		public DisplayDataUpdater(String fieldType) {
			mFieldType = fieldType;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}

		@Override
		public void afterTextChanged(Editable s) {
			String text = s.toString();

			if (HostDatabase.FIELD_HOST_NICKNAME.equals(mFieldType)) {
				mDisplayData.nickname = text;
			} else {
				try {
					mDisplayData.fontSize = Integer.parseInt(text);
				} catch (NumberFormatException e) {}
			}

			mListener.onDisplaySettingsChanged(mDisplayData);
		}
	}

}
