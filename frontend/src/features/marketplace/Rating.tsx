export function Rating({ averageRating, reviewCount }: { averageRating?: number | null; reviewCount?: number }) {
  return <span className="market-rating">{averageRating == null || !reviewCount ? "Chưa có đánh giá" : <><span aria-hidden="true">★</span> {averageRating.toFixed(1)} / 5 · {reviewCount} đánh giá</>}</span>;
}
