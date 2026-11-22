"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import {
  getReviewEligibility, listReviews, postReview, validateComment,
  type ProductReview, type ReviewEligibility, ReviewHttpError,
} from "@/lib/reviews";
import "./reviews.css";

export default function ProductReviews({ productId }: { productId: number }) {
  const { user, getToken, logout } = useAuth();
  const [reviews, setReviews] = useState<ProductReview[]>([]);
  const [eligibility, setEligibility] = useState<ReviewEligibility | null>(null);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState("");
  const [eligibilityError, setEligibilityError] = useState("");
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [fieldError, setFieldError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setListError("");
    listReviews(productId, controller.signal)
      .then((items) => { if (!controller.signal.aborted) setReviews(items); })
      .catch(() => { if (!controller.signal.aborted) setListError("No se pudieron cargar las reseñas."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [productId, refresh]);

  useEffect(() => {
    const controller = new AbortController();
    const token = user?.role === "CLIENT" ? getToken() : null;
    setEligibility(null);
    setEligibilityError("");
    if (!token) return () => controller.abort();
    getReviewEligibility(token, productId, controller.signal)
      .then((value) => { if (!controller.signal.aborted && getToken() === token) setEligibility(value); })
      .catch((error: unknown) => {
        if (!controller.signal.aborted && getToken() === token) {
          setEligibilityError(error instanceof Error ? error.message : "No se pudo comprobar la elegibilidad.");
        }
      });
    return () => controller.abort();
  }, [productId, user, refresh]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !eligibility?.eligible) return;
    const error = rating < 1 || rating > 5 ? "Selecciona una calificación entre 1 y 5."
      : validateComment(comment);
    setFieldError(error ?? "");
    setSubmitError("");
    setNotice("");
    if (error) return;
    const token = getToken();
    if (!token) { setSubmitError("Inicia sesión nuevamente."); return; }
    setBusy(true);
    try {
      await postReview(token, productId, rating, comment);
      if (getToken() === token) {
        setNotice("Tu reseña se publicó correctamente.");
        setRating(0);
        setComment("");
        setRefresh((n) => n + 1);
      }
    } catch (cause: unknown) {
      if (cause instanceof ReviewHttpError && cause.status === 401 && getToken() === token) {
        await logout();
      }
      setSubmitError(cause instanceof Error ? cause.message : "No se pudo publicar la reseña.");
      if (cause instanceof ReviewHttpError && cause.status === 409) setRefresh((n) => n + 1);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="product-reviews" aria-labelledby="product-reviews-title">
      <h2 id="product-reviews-title">Reseñas del producto</h2>
      {loading ? <p role="status">Cargando reseñas…</p>
        : listError ? <div role="alert"><p>{listError}</p>
            <button type="button" onClick={() => setRefresh((n) => n + 1)}>Reintentar</button>
          </div>
        : reviews.length === 0 ? <p>Todavía no hay reseñas publicadas.</p>
        : <ol className="product-reviews__list">
            {reviews.map((review, index) => (
              <li key={review.createdAt + "-" + index} className="product-reviews__item">
                <p className="product-reviews__rating" aria-label={review.rating + " de 5 estrellas"}>
                  <span aria-hidden="true">{"★".repeat(review.rating)}{"☆".repeat(5 - review.rating)}</span>
                  <span> {review.rating}/5</span>
                </p>
                <p className="product-reviews__comment">{review.comment}</p>
                <time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString("es-CR")}</time>
              </li>
            ))}
          </ol>}
      <div className="product-reviews__form-area">
        <h3>Escribe una reseña</h3>
        {!user ? <p><Link href="/login">Inicia sesión</Link> para consultar si puedes reseñar.</p>
          : user.role !== "CLIENT" ? <p>Solo las cuentas de cliente pueden publicar reseñas.</p>
          : eligibilityError ? <p role="alert">{eligibilityError} <button type="button" onClick={() => setRefresh((n) => n + 1)}>Reintentar</button></p>
          : !eligibility ? <p role="status">Comprobando requisitos para publicar…</p>
          : !eligibility.eligible ? <p role="status">{eligibility.hasActiveReview
              ? "Ya publicaste una reseña para este producto."
              : "Puedes reseñar productos de pedidos confirmados con entrega manual verificada."}</p>
          : <form className="product-reviews__form" onSubmit={(event) => void submit(event)} noValidate>
              <fieldset disabled={busy}>
                <legend>Calificación (obligatoria)</legend>
                <div className="product-reviews__choices">
                  {[1, 2, 3, 4, 5].map((value) => (
                    <label key={value}>
                      <input type="radio" name={"review-rating-" + productId} value={value}
                        checked={rating === value} onChange={() => { setRating(value); setFieldError(""); }}
                        required aria-describedby={fieldError ? "review-field-error" : undefined} />
                      {value} <span aria-hidden="true">★</span>
                    </label>
                  ))}
                </div>
              </fieldset>
              <label htmlFor={"review-comment-" + productId}>Comentario (obligatorio)</label>
              <textarea id={"review-comment-" + productId} value={comment} rows={5}
                minLength={5} maxLength={1000} required
                aria-invalid={!!fieldError}
                aria-describedby={fieldError ? "review-field-error" : "review-comment-help"}
                onChange={(event) => { setComment(event.target.value); setFieldError(""); }} />
              <p id="review-comment-help">Entre 5 y 1000 caracteres. {comment.length}/1000.</p>
              {fieldError && <p id="review-field-error" role="alert">{fieldError}</p>}
              {submitError && <p role="alert">{submitError}</p>}
              <button type="submit" disabled={busy}>{busy ? "Publicando…" : "Publicar reseña"}</button>
            </form>}
        {notice && <p role="status" className="product-reviews__success">{notice}</p>}
      </div>
    </section>
  );
}
