import { useQuery } from '@tanstack/react-query'
import { getHealth } from '../api/healthApi'

export function FoundationPage() {
  const health = useQuery({ queryKey: ['health'], queryFn: getHealth, retry: false })

  return (
    <main className="min-h-screen bg-slate-50 px-6 py-20 text-slate-900">
      <div className="mx-auto max-w-xl rounded-2xl border border-slate-200 bg-white p-8">
        <h1 className="text-3xl font-bold">BusGo</h1>
        <p className="mt-3 text-slate-600">Hệ thống đang được chuẩn bị.</p>
        <p role="status" className="mt-6">
          {health.isPending ? 'Đang kiểm tra kết nối…' : health.isError
            ? 'Không thể kết nối đến máy chủ.' : health.data.status === 'UP'
              ? 'Kết nối máy chủ thành công.' : 'Máy chủ chưa sẵn sàng.'}
        </p>
        {health.isError && <button type="button" onClick={() => void health.refetch()}
          className="mt-4 rounded-lg bg-blue-700 px-4 py-2 font-medium text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700">Thử lại</button>}
      </div>
    </main>
  )
}
