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

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.connectbot.bean.HostBean;

public class HostEditorFragment extends Fragment
		implements HostUriEditorFragment.Listener, HostDisplayEditorFragment.Listener {
	private static final String ARG_EXISTING_HOST = "isCreating";

	private HostBean mHost;
	private boolean mIsCreating;

	private Listener mListener;

	public static HostEditorFragment newInstance(HostBean existingHost) {
		HostEditorFragment fragment = new HostEditorFragment();
		Bundle args = new Bundle();
		if (existingHost != null) {
			args.putParcelable(ARG_EXISTING_HOST, existingHost.getValues());
		}
		fragment.setArguments(args);
		return fragment;
	}

	public HostEditorFragment() {

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Parcelable existingHostParcelable = getArguments().getParcelable(ARG_EXISTING_HOST);
		mIsCreating = existingHostParcelable == null;
		if (existingHostParcelable != null) {
			mHost = HostBean.fromContentValues((ContentValues) existingHostParcelable);
		} else {
			mHost = new HostBean();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_host_editor, container, false);

		if (savedInstanceState == null) {
			HostUriEditorFragment uriEditor =
					HostUriEditorFragment.createInstance(mIsCreating ? null : mHost);
			getChildFragmentManager().beginTransaction()
					.add(R.id.uri_editor_container, uriEditor).commit();

			HostDisplayEditorFragment displayEditor =
					HostDisplayEditorFragment.createInstance(mIsCreating ? null : mHost);
			getChildFragmentManager().beginTransaction()
					.add(R.id.display_editor_container, displayEditor).commit();
		}

		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			mListener = (Listener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString() + " must implement Listener");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	@Override
	public void onValidUriEntered(HostUriEditorFragment.UriData data) {

	}

	@Override
	public void onInvalidUriEntered() {

	}

	@Override
	public void onDisplaySettingsChanged(HostDisplayEditorFragment.DisplayData data) {

	}

	public interface Listener {
		public void onHostUpdated(HostBean host);
	}
}
