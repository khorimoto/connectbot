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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.connectbot.bean.HostBean;
import org.connectbot.transport.AbsTransport;
import org.connectbot.transport.Local;
import org.connectbot.transport.SSH;
import org.connectbot.transport.Telnet;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;

public class HostUriEditorFragment extends Fragment {

	private static final String TAG = "HostUriEditorFragment";

	private static final String IS_EXPANDED = "isExpanded";

	// Listener for host URI changes.
	private Listener mListener;

	// The URI data currently present in the widget.
	private UriData mUriData;

	// Whether the URI parts subsection is expanded.
	private boolean mIsExpanded = false;

	// Whether a text edit is in progress. When the quick-connect field is being edited, changes
	// automatically propagate to the URI part fields; likewise, when the URI part fields are
	// edited, changes are propagated to the quick-connect field. This boolean safeguards against
	// infinite loops which can be caused by one field changing the other field, which changes the
	// first field, etc.
	private boolean mTextEditInProgress = false;

	private Spinner mTransportSpinner;
	private TextInputLayout mQuickConnectContainer;
	private EditText mQuickConnectField;
	private ImageButton mExpandCollapseButton;
	private View mUriPartsContainer;
	private View mUsernameContainer;
	private EditText mUsernameField;
	private View mHostnameContainer;
	private EditText mHostnameField;
	private View mPortContainer;
	private EditText mPortField;

	public HostUriEditorFragment() {}

	/**
	 * Creates a HostUriEditorFragment. This static function should be used instead of calling the
	 * constructor directly.
	 * @param existingHost The existing host if one exists, or null if a new host is being created.
	 */
	public static HostUriEditorFragment createInstance(HostBean existingHost) {
		HostUriEditorFragment fragment = new HostUriEditorFragment();

		Bundle args = new Bundle();
		if (existingHost != null) {
			args.putString(HostDatabase.FIELD_HOST_PROTOCOL, existingHost.getProtocol());
			args.putString(HostDatabase.FIELD_HOST_USERNAME, existingHost.getUsername());
			args.putString(HostDatabase.FIELD_HOST_HOSTNAME, existingHost.getHostname());
			args.putString(HostDatabase.FIELD_HOST_NICKNAME, existingHost.getNickname());
			args.putInt(HostDatabase.FIELD_HOST_PORT, existingHost.getPort());
		}
		fragment.setArguments(args);

		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle bundle = savedInstanceState == null ? getArguments() : savedInstanceState;

		mUriData = new UriData();
		mUriData.protocol = bundle.getString(HostDatabase.FIELD_HOST_PROTOCOL);
		mUriData.username = bundle.getString(HostDatabase.FIELD_HOST_USERNAME);
		mUriData.hostname = bundle.getString(HostDatabase.FIELD_HOST_HOSTNAME);
		mUriData.nickname = bundle.getString(HostDatabase.FIELD_HOST_NICKNAME);
		mUriData.port = bundle.getInt(HostDatabase.FIELD_HOST_PORT);

		mIsExpanded = bundle.getBoolean(IS_EXPANDED);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_host_uri_editor, container, false);

		mTransportSpinner = (Spinner) view.findViewById(R.id.transport_selector);
		ArrayAdapter<String> transportSelection = new ArrayAdapter<>(
				getActivity(),
				android.R.layout.simple_spinner_item,
				TransportFactory.getTransportNames());
		transportSelection.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTransportSpinner.setAdapter(transportSelection);
		mTransportSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String protocol = (String) mTransportSpinner.getSelectedItem();
				if (protocol == null) {
					// During initialization, protocol can be null before the list of dropdown items
					// has been generated. Return early in that case.
					return;
				}

				mUriData.protocol = protocol;
				mUriData.port = TransportFactory.getTransport(protocol).getDefaultPort();

				mQuickConnectContainer.setHint(
						TransportFactory.getFormatHint(protocol, getActivity()));

				// Different protocols have different field types, so show only the fields needed.
				if (SSH.getProtocolName().equals(protocol)) {
					mUsernameContainer.setVisibility(View.VISIBLE);
					mHostnameContainer.setVisibility(View.VISIBLE);
					mPortContainer.setVisibility(View.VISIBLE);
					mExpandCollapseButton.setVisibility(View.VISIBLE);
				} else if (Telnet.getProtocolName().equals(protocol)) {
					mUsernameContainer.setVisibility(View.GONE);
					mHostnameContainer.setVisibility(View.VISIBLE);
					mPortContainer.setVisibility(View.VISIBLE);
					mExpandCollapseButton.setVisibility(View.VISIBLE);
				} else {
					// Local protocol has only one field, so no need to show the URI parts
					// container.
					setUriPartsContainerExpanded(false);
					mExpandCollapseButton.setVisibility(View.INVISIBLE);
				}

				mListener.onInvalidUriEntered();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		mQuickConnectContainer =
				(TextInputLayout) view.findViewById(R.id.quickconnect_field_container);

		mQuickConnectField = (EditText) view.findViewById(R.id.quickconnect_field);
		mQuickConnectField.setText(mUriData.toString());
		mQuickConnectField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				if (mTransportSpinner.getSelectedItem() == null) {
					// During initialization, protocol can be null before the list of dropdown items
					// has been generated. Return early in that case.
					return;
				}

				if (!mTextEditInProgress) {
					mUriData.applyQuickConnectString(
							s.toString(), (String) mTransportSpinner.getSelectedItem());

					mTextEditInProgress = true;
					mUsernameField.setText(mUriData.username);
					mHostnameField.setText(mUriData.hostname);
					mPortField.setText(Integer.toString(mUriData.port));
					mTextEditInProgress = false;
				}

				if (mUriData.isValidUri()) {
					mListener.onValidUriEntered(mUriData);
				} else {
					mListener.onInvalidUriEntered();
				}
			}
		});

		mExpandCollapseButton = (ImageButton) view.findViewById(R.id.expand_collapse_button);
		mExpandCollapseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setUriPartsContainerExpanded(!mIsExpanded);
			}
		});

		mUriPartsContainer = view.findViewById(R.id.uri_parts_container);

		mUsernameContainer = view.findViewById(R.id.username_field_container);
		mUsernameField = (EditText) view.findViewById(R.id.username_edit_text);
		mUsernameField.setText(mUriData.username);
		mUsernameField.addTextChangedListener(new UriDataUpdater(HostDatabase.FIELD_HOST_USERNAME));

		mHostnameContainer = view.findViewById(R.id.hostname_field_container);
		mHostnameField = (EditText) view.findViewById(R.id.hostname_edit_text);
		mHostnameField.setText(mUriData.hostname);
		mHostnameField.addTextChangedListener(new UriDataUpdater(HostDatabase.FIELD_HOST_HOSTNAME));

		mPortContainer = view.findViewById(R.id.port_field_container);
		mPortField = (EditText) view.findViewById(R.id.port_edit_text);
		mPortField.setText(Integer.toString(mUriData.port));
		mPortField.addTextChangedListener(new UriDataUpdater(HostDatabase.FIELD_HOST_PORT));

		setUriPartsContainerExpanded(mIsExpanded);

		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			mListener = (Listener) getParentFragment();
		} catch (ClassCastException e) {
			throw new ClassCastException(getParentFragment().toString() + " must implement Listener");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		savedInstanceState.putString(HostDatabase.FIELD_HOST_PROTOCOL, mUriData.protocol);
		savedInstanceState.putString(HostDatabase.FIELD_HOST_USERNAME, mUriData.username);
		savedInstanceState.putString(HostDatabase.FIELD_HOST_HOSTNAME, mUriData.hostname);
		savedInstanceState.putString(HostDatabase.FIELD_HOST_NICKNAME, mUriData.nickname);
		savedInstanceState.putInt(HostDatabase.FIELD_HOST_PORT, mUriData.port);
		savedInstanceState.putBoolean(IS_EXPANDED, mIsExpanded);
	}

	private void setUriPartsContainerExpanded(boolean expanded) {
		mIsExpanded = expanded;

		if (mIsExpanded) {
			mExpandCollapseButton.setImageResource(R.drawable.ic_expand_less);
			mUriPartsContainer.setVisibility(View.VISIBLE);
		} else {
			mExpandCollapseButton.setImageResource(R.drawable.ic_expand_more);
			mUriPartsContainer.setVisibility(View.GONE);
		}
	}

	/**
	 * Contains the components of the URI entered.
	 */
	public static class UriData {
		public String protocol;
		public String username;
		public String hostname;
		public String nickname;
		public int port;

		/**
		 * @return Whether this represents a valid URI.
		 */
		public boolean isValidUri() {
			if (protocol == null)
				return false;

			return TransportFactory.getUri(protocol, toString()) != null;
		}

		/**
		 * Applies the quick-connect URI entered in the field by copying its URI parts to the
		 * associated fields in this class.
		 * @param quickConnectString The URI entered in the quick-connect field.
		 * @param protocol The protocol for this connection.
		 */
		public void applyQuickConnectString(String quickConnectString, String protocol) {
			if (quickConnectString == null || protocol == null)
				return;

			Uri uri = TransportFactory.getUri(protocol, quickConnectString);
			if (uri == null) {
				// If the URI was invalid, null out the associated fields.
				username = null;
				hostname = null;
				nickname = null;
				port = TransportFactory.getTransport(protocol).getDefaultPort();
				return;
			}

			HostBean host = TransportFactory.getTransport(protocol).createHost(uri);
			this.protocol = protocol;
			username = host.getUsername();
			hostname = host.getHostname();
			nickname = host.getNickname();
			port = host.getPort();
		}

		@Override
		public String toString() {
			if (protocol == null)
				return "";

			int defaultPort = TransportFactory.getTransport(protocol).getDefaultPort();

			if (SSH.getProtocolName().equals(protocol)) {
				if (username == null || hostname == null ||
						username.equals("") || hostname.equals(""))
					return "";

				if (port == defaultPort)
					return username + "@" + hostname;
				else
					return username + "@" + hostname + ":" + port;
			} else if (Telnet.getProtocolName().equals(protocol)) {
				if (hostname == null || hostname.equals(""))
					return "";
				else if (port == defaultPort)
					return hostname;
				else
					return hostname + ":" + port;
			} else if (Local.getProtocolName().equals(protocol)) {
				return nickname;
			} else {
				throw new RuntimeException("Invalid protocol");
			}
		}
	}

	/**
	 * Interface for listeners of URI changes.
	 */
	public interface Listener {
		public void onValidUriEntered(UriData data);
		public void onInvalidUriEntered();
	}

	private class UriDataUpdater implements TextWatcher {

		private final String mFieldType;

		public UriDataUpdater(String fieldType) {
			mFieldType = fieldType;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}

		@Override
		public void afterTextChanged(Editable s) {
			String text = s.toString();

			if (HostDatabase.FIELD_HOST_USERNAME.equals(mFieldType)) {
				mUriData.username = text;
			} else if (HostDatabase.FIELD_HOST_HOSTNAME.equals(mFieldType)) {
				mUriData.hostname = text;
			} else if (HostDatabase.FIELD_HOST_PORT.equals(mFieldType)) {
				try {
					mUriData.port = Integer.parseInt(text);
				} catch (NumberFormatException e) {}
			} else {
				throw new RuntimeException("Invalid field type.");
			}

			if (!mTextEditInProgress) {
				mTextEditInProgress = true;
				mQuickConnectField.setText(mUriData.toString());
				mTextEditInProgress = false;
			}

			if (mUriData.isValidUri()) {
				mListener.onValidUriEntered(mUriData);
			} else {
				mListener.onInvalidUriEntered();
			}
		}
	}

}
