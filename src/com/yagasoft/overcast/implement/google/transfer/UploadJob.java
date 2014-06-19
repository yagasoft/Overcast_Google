/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			License terms are in a separate file (LICENCE.md)
 *
 *		Project/File: Overcast/com.yagasoft.overcast.implement.google/UploadJob.java
 *
 *			Modified: Apr 15, 2014 (1:54:35 PM)
 *			   Using: Eclipse J-EE / JDK 7 / Windows 8.1 x64
 */

package com.yagasoft.overcast.implement.google.transfer;


import java.util.concurrent.Future;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.yagasoft.overcast.base.container.local.LocalFile;
import com.yagasoft.overcast.base.container.transfer.event.TransferState;
import com.yagasoft.overcast.implement.google.container.RemoteFile;
import com.yagasoft.overcast.implement.google.container.RemoteFolder;


/**
 * @see com.yagasoft.overcast.base.container.transfer.UploadJob
 */
public class UploadJob extends com.yagasoft.overcast.base.container.transfer.UploadJob<Drive.Files.Insert, File>
{

	Future<Void>	canceller;

	/**
	 * Instantiates a new upload job.
	 *
	 * @param localFile
	 *            Local file.
	 * @param remoteFile
	 *            Remote file.
	 * @param parent
	 *            Parent.
	 * @param overwrite
	 *            Overwrite.
	 * @param cspTransferer
	 *            Csp transferer.
	 */
	public UploadJob(LocalFile localFile, RemoteFile remoteFile, RemoteFolder parent, boolean overwrite
			, Drive.Files.Insert cspTransferer)
	{
		super(localFile, remoteFile, parent, overwrite, cspTransferer);
	}

	@Override
	public void cancelTransfer() throws UnsupportedOperationException
	{
		canceller.cancel(true);
		notifyProgressListeners(TransferState.CANCELLED, 0);
	}

	/**
	 * @return the canceller
	 */
	public Future<Void> getCanceller()
	{
		return canceller;
	}

	/**
	 * @param canceller
	 *            the canceller to set
	 */
	public void setCanceller(Future<Void> canceller)
	{
		this.canceller = canceller;
	}

}
