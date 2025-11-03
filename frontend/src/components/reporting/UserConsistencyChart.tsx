/**
 * UserConsistencyChart - Bar chart showing voting variance by participant.
 *
 * Displays each user's vote consistency (variance) as a bar chart.
 * Lower variance indicates more consistent voting patterns.
 * Pro tier only.
 */

import { useMemo } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';

interface UserConsistencyChartProps {
  userConsistencyMap: Record<string, number>;
}

interface ChartDataPoint {
  name: string;
  variance: number;
}

/**
 * Transform user consistency map to array format for Recharts.
 */
function transformData(userConsistencyMap: Record<string, number>): ChartDataPoint[] {
  return Object.entries(userConsistencyMap)
    .map(([name, variance]) => ({
      name,
      variance: Number(variance.toFixed(2)), // Round to 2 decimal places
    }))
    .sort((a, b) => a.variance - b.variance); // Sort by variance (ascending)
}

/**
 * Custom tooltip to show detailed variance information.
 */
function CustomTooltip({ active, payload }: any) {
  if (active && payload && payload.length) {
    const data = payload[0].payload as ChartDataPoint;
    return (
      <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg p-3">
        <p className="text-sm font-semibold text-gray-900 dark:text-white mb-1">
          {data.name}
        </p>
        <p className="text-sm text-gray-600 dark:text-gray-400">
          Variance: <span className="font-medium text-blue-600 dark:text-blue-400">{data.variance}</span>
        </p>
        <p className="text-xs text-gray-500 dark:text-gray-500 mt-1">
          {data.variance < 2 ? 'Very consistent' : data.variance < 5 ? 'Moderately consistent' : 'Variable voting'}
        </p>
      </div>
    );
  }
  return null;
}

export default function UserConsistencyChart({ userConsistencyMap }: UserConsistencyChartProps) {
  const chartData = useMemo(() => transformData(userConsistencyMap), [userConsistencyMap]);

  if (chartData.length === 0) {
    return (
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">
          User Consistency
        </h2>
        <p className="text-gray-500 dark:text-gray-400 text-center py-8">
          No consistency data available for this session.
        </p>
      </section>
    );
  }

  return (
    <section className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
      <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
        User Consistency
      </h2>
      <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">
        Vote variance by participant (lower is more consistent)
      </p>

      <ResponsiveContainer width="100%" height={300}>
        <BarChart
          data={chartData}
          margin={{ top: 20, right: 30, left: 20, bottom: 60 }}
        >
          <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
          <XAxis
            dataKey="name"
            angle={-45}
            textAnchor="end"
            height={80}
            className="text-xs fill-gray-600 dark:fill-gray-400"
          />
          <YAxis
            label={{ value: 'Variance', angle: -90, position: 'insideLeft' }}
            className="text-xs fill-gray-600 dark:fill-gray-400"
          />
          <Tooltip content={<CustomTooltip />} />
          <Legend
            wrapperStyle={{ paddingTop: '20px' }}
            iconType="rect"
          />
          <Bar
            dataKey="variance"
            fill="#3b82f6"
            name="Vote Variance"
            radius={[4, 4, 0, 0]}
          />
        </BarChart>
      </ResponsiveContainer>

      {/* Legend explanation */}
      <div className="mt-4 text-xs text-gray-500 dark:text-gray-400">
        <p>
          <strong>Variance</strong> measures how spread out a participant's votes are.
          Lower variance indicates more consistent voting patterns across rounds.
        </p>
      </div>
    </section>
  );
}
