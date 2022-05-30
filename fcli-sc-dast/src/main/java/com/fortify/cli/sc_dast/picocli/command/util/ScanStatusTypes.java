
package com.fortify.cli.sc_dast.picocli.command.util;

// TODO Instead of depending on indexes, each enum entry should explicitly define the scan status type integer 
// TODO What would be the most appropriate package for this enum?
public enum ScanStatusTypes {
	Queued, Pending, Paused, Running, Complete, Interrupted, Unknown,
	ImportingScanResults, ImportScanResultsFailed, FailedToStart, PausingScan,
	ResumingScan, CompletingScan, ResumeScanQueued, ForcedComplete, FailedToResume, LicenseUnavailable;

	public static String getStatusString(Integer index){
		return ScanStatusTypes.values()[index-1].toString();
	}

}