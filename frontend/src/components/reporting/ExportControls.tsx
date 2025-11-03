/**
 * ExportControls - Export session reports to CSV or PDF.
 *
 * Handles export job creation, status polling, and download link display.
 * Shows loading state during job processing and error messages on failure.
 * Pro tier only.
 */

import { useState } from 'react';
import { ArrowDownTrayIcon, CheckCircleIcon, XCircleIcon } from '@heroicons/react/24/outline';
import { useExportJob, useExportJobStatus } from '@/services/reportingApi';
import type { ExportFormat } from '@/types/reporting';

interface ExportControlsProps {
  sessionId: string;
}

export default function ExportControls({ sessionId }: ExportControlsProps) {
  const [jobId, setJobId] = useState<string | null>(null);
  const [exportFormat, setExportFormat] = useState<ExportFormat | null>(null);

  // Export job mutation
  const exportMutation = useExportJob({
    onSuccess: (data) => {
      setJobId(data.job_id);
    },
    onError: (error) => {
      console.error('Export failed:', error);
    },
  });

  // Poll job status (auto-polls every 2 seconds while pending/processing)
  const { data: jobStatus } = useExportJobStatus(jobId);

  /**
   * Handle export button click.
   */
  const handleExport = (format: ExportFormat) => {
    setExportFormat(format);
    setJobId(null); // Reset previous job
    exportMutation.mutate({
      session_id: sessionId,
      format,
    });
  };

  /**
   * Reset export state to allow new export.
   */
  const handleReset = () => {
    setJobId(null);
    setExportFormat(null);
    exportMutation.reset();
  };

  return (
    <section className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
      <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">
        Export Report
      </h2>

      {/* Export Buttons */}
      {!jobId && !exportMutation.isPending && (
        <div className="flex flex-wrap gap-3">
          <button
            onClick={() => handleExport('CSV')}
            disabled={exportMutation.isPending}
            className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <ArrowDownTrayIcon className="h-5 w-5 mr-2" />
            Export CSV
          </button>

          <button
            onClick={() => handleExport('PDF')}
            disabled={exportMutation.isPending}
            className="inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 text-sm font-medium rounded-md shadow-sm text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <ArrowDownTrayIcon className="h-5 w-5 mr-2" />
            Export PDF
          </button>
        </div>
      )}

      {/* Loading State - Creating Job */}
      {exportMutation.isPending && (
        <div className="flex items-center text-sm text-gray-600 dark:text-gray-400">
          <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-600 mr-3"></div>
          Creating export job...
        </div>
      )}

      {/* Job Status - Pending/Processing */}
      {jobId && jobStatus && (jobStatus.status === 'PENDING' || jobStatus.status === 'PROCESSING') && (
        <div className="space-y-3">
          <div className="flex items-center text-sm text-gray-600 dark:text-gray-400">
            <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-600 mr-3"></div>
            Processing {exportFormat} export...
          </div>
          <p className="text-xs text-gray-500 dark:text-gray-500">
            This may take a few moments. You can leave this page and the export will continue in the background.
          </p>
        </div>
      )}

      {/* Job Status - Completed */}
      {jobId && jobStatus && jobStatus.status === 'COMPLETED' && jobStatus.download_url && (
        <div className="space-y-3">
          <div className="flex items-center text-sm text-green-600 dark:text-green-400">
            <CheckCircleIcon className="h-5 w-5 mr-2" />
            Export completed successfully!
          </div>

          <div className="flex flex-col sm:flex-row gap-3">
            <a
              href={jobStatus.download_url}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-green-600 hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500 transition-colors"
            >
              <ArrowDownTrayIcon className="h-5 w-5 mr-2" />
              Download {exportFormat}
            </a>

            <button
              onClick={handleReset}
              className="inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 text-sm font-medium rounded-md shadow-sm text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
            >
              Export Another Format
            </button>
          </div>

          <div className="flex items-start space-x-2 text-xs text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-md p-3">
            <svg
              className="h-4 w-4 flex-shrink-0 mt-0.5"
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                fillRule="evenodd"
                d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                clipRule="evenodd"
              />
            </svg>
            <p>Download link expires in 24 hours</p>
          </div>
        </div>
      )}

      {/* Job Status - Failed */}
      {jobId && jobStatus && jobStatus.status === 'FAILED' && (
        <div className="space-y-3">
          <div className="flex items-center text-sm text-red-600 dark:text-red-400">
            <XCircleIcon className="h-5 w-5 mr-2" />
            Export failed
          </div>

          {jobStatus.error_message && (
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Error: {jobStatus.error_message}
            </p>
          )}

          <button
            onClick={handleReset}
            className="inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 text-sm font-medium rounded-md shadow-sm text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
          >
            Try Again
          </button>
        </div>
      )}

      {/* Mutation Error (not job-related) */}
      {exportMutation.isError && !jobId && (
        <div className="space-y-3">
          <div className="flex items-center text-sm text-red-600 dark:text-red-400">
            <XCircleIcon className="h-5 w-5 mr-2" />
            Failed to create export job
          </div>

          <p className="text-sm text-gray-600 dark:text-gray-400">
            {exportMutation.error?.message || 'An unexpected error occurred'}
          </p>

          <button
            onClick={() => exportMutation.reset()}
            className="inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 text-sm font-medium rounded-md shadow-sm text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
          >
            Try Again
          </button>
        </div>
      )}
    </section>
  );
}
