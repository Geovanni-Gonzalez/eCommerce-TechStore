"use client";

import Link from "next/link";
import {useEffect,useState,type FormEvent} from "react";
import {useAuth} from "@/components/AuthProvider";
import {formatCatalogCrc} from "@/lib/catalog";
import {
  AdminCatalogError,createAdminCategory,deleteAdminCategory,
  listAdminCategories,listAdminProducts,renameAdminCategory,
  saveAdminProduct,setAdminProductActive,
  type AdminCategory,type AdminProduct,type AdminProductPage,type ProductWrite,
} from "@/lib/admin-catalog";

type ProductForm={
  sku:string;name:string;description:string;categoryId:string;
  brand:string;priceCrc:string;
};
const EMPTY:ProductForm={
  sku:"",name:"",description:"",categoryId:"",brand:"",priceCrc:"",
};
function formFromProduct(product:AdminProduct):ProductForm {
  return {
    sku:product.sku,name:product.name,description:product.description??"",
    categoryId:product.categoryId?.toString()??"",
    brand:product.brand??"",priceCrc:String(product.priceCrc),
  };
}
function validateForm(value:ProductForm):ProductWrite {
  const sku=value.sku.trim().toUpperCase();
  const name=value.name.trim();
  const brand=value.brand.trim();
  const description=value.description.trim();
  const amount=value.priceCrc.trim();
  if(!/^[A-Z0-9][A-Z0-9_-]{1,63}$/.test(sku))
    throw new Error("SKU: entre 2 y 64 caracteres, solo letras, números, guion y guion bajo.");
  if(name.length<1||name.length>160)throw new Error("Nombre: entre 1 y 160 caracteres.");
  if(brand.length>100||description.length>3000)
    throw new Error("Marca o descripción supera el límite admitido.");
  if(!/^(0|[1-9][0-9]{0,9})(\.[0-9]{1,2})?$/.test(amount))
    throw new Error("Precio CRC inválido: utiliza hasta dos decimales, sin separadores.");
  const id=value.categoryId?Number(value.categoryId):null;
  if(id!==null && (!Number.isSafeInteger(id)||id<1))
    throw new Error("Selecciona una categoría válida.");
  return {
    sku,name,brand:brand||null,description:description||null,
    categoryId:id,priceCrc:Number(amount),
  };
}

function ProductEditor({
  selected,categories,onSaved,onCancel,run,disabled,
}:{
  selected:AdminProduct|null;categories:AdminCategory[];
  onSaved:(message:string)=>void;onCancel:()=>void;
  run:<T>(job:()=>Promise<T>)=>Promise<T|undefined>;
  disabled:boolean;
}) {
  const [form,setForm]=useState<ProductForm>(()=>selected?formFromProduct(selected):{...EMPTY});
  const [error,setError]=useState("");
  function set(field:keyof ProductForm,text:string) {
    setForm(previous=>({...previous,[field]:text}));
    setError("");
  }
  async function submit(event:FormEvent<HTMLFormElement>) {
    event.preventDefault();
    let payload:ProductWrite;
    try{payload=validateForm(form);}catch(e:unknown){
      setError(e instanceof Error?e.message:"Comprueba los datos.");
      return;
    }
    const result=await run(()=>saveAdminProductToken(payload,selected?.id));
    if(result)onSaved(selected?"Producto actualizado.":"Producto creado con stock inicial 0.");
  }
  // El token se toma del provider en el momento de guardar, no del DOM.
  const {getToken}=useAuth();
  async function saveAdminProductToken(payload:ProductWrite,id?:number) {
    const token=getToken();
    if(!token)throw new Error("Inicia sesión nuevamente.");
    return saveAdminProduct(token,payload,id);
  }
  return (
    <section className="admin-panel" aria-labelledby="admin-editor-title">
      <h2 id="admin-editor-title">
        {selected?"Editar producto #"+selected.id:"Crear producto"}
      </h2>
      <p className="admin-note">Las existencias no se editan aquí para proteger
        reservas y movimientos; los nuevos productos comienzan con stock cero.</p>
      {error && <p role="alert" className="admin-error">{error}</p>}
      <form className="admin-form" onSubmit={event=>void submit(event)}>
        <div className="admin-field">
          <label htmlFor="ap-sku">SKU</label>
          <input id="ap-sku" value={form.sku} required maxLength={64}
            onChange={e=>set("sku",e.target.value)} disabled={disabled}/>
        </div>
        <div className="admin-field">
          <label htmlFor="ap-name">Nombre del producto</label>
          <input id="ap-name" value={form.name} required maxLength={160}
            onChange={e=>set("name",e.target.value)} disabled={disabled}/>
        </div>
        <div className="admin-field">
          <label htmlFor="ap-price">Precio (colones CRC)</label>
          <input id="ap-price" type="number" inputMode="decimal" step=".01"
            min="0" max="9999999999.99" required value={form.priceCrc}
            onChange={e=>set("priceCrc",e.target.value)} disabled={disabled}/>
        </div>
        <div className="admin-field">
          <label htmlFor="ap-category">Categoría</label>
          <select id="ap-category" value={form.categoryId} disabled={disabled}
            onChange={e=>set("categoryId",e.target.value)}>
            <option value="">Sin categoría</option>
            {categories.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <div className="admin-field">
          <label htmlFor="ap-brand">Marca (opcional)</label>
          <input id="ap-brand" maxLength={100} value={form.brand}
            onChange={e=>set("brand",e.target.value)} disabled={disabled}/>
        </div>
        <div className="admin-field admin-field--wide">
          <label htmlFor="ap-description">Descripción</label>
          <textarea id="ap-description" rows={4} maxLength={3000}
            value={form.description} disabled={disabled}
            onChange={e=>set("description",e.target.value)}/>
        </div>
        <div className="admin-actions">
          <button className="admin-primary" disabled={disabled} type="submit">
            {selected?"Guardar cambios":"Crear producto"}
          </button>
          <button className="admin-secondary" type="button" disabled={disabled}
            onClick={onCancel}>Cancelar</button>
        </div>
      </form>
    </section>
  );
}

export default function AdminCatalogManager({token}:{token:string}) {
  const {getToken,logout}=useAuth();
  const [page,setPage]=useState(0);
  const [version,setVersion]=useState(0);
  const [products,setProducts]=useState<{page:number;value:AdminProductPage}|null>(null);
  const [categories,setCategories]=useState<AdminCategory[]|null>(null);
  const [error,setError]=useState("");
  const [message,setMessage]=useState("");
  const [busy,setBusy]=useState(false);
  const [editor,setEditor]=useState<{key:string;selected:AdminProduct|null}|null>(null);
  const [pendingStatus,setPendingStatus]=useState<AdminProduct|null>(null);
  const [categoryName,setCategoryName]=useState("");
  const [editCategory,setEditCategory]=useState<AdminCategory|null>(null);
  const [categoryEditName,setCategoryEditName]=useState("");
  const [deleteCategory,setDeleteCategory]=useState<AdminCategory|null>(null);
  const stale=()=>getToken()!==token;

  useEffect(()=>{
    const controller=new AbortController();
    void Promise.all([
      listAdminProducts(token,page,controller.signal),
      listAdminCategories(token,controller.signal),
    ]).then(([products,categories])=>{
      if(!controller.signal.aborted){
        setProducts({page,value:products});
        setCategories(categories);
      }
    }).catch((cause:unknown)=>{
      if(controller.signal.aborted)return;
      setError(cause instanceof Error?cause.message:"No se pudo cargar la administración.");
      if(cause instanceof AdminCatalogError && cause.status===401)void logout();
    });
    return ()=>controller.abort();
  },[token,page,version,logout]);

  async function run<T>(job:()=>Promise<T>):Promise<T|undefined> {
    if(busy||stale())return;
    setBusy(true);setError("");setMessage("");
    try{
      const result=await job();
      if(stale())return;
      setVersion(n=>n+1);
      return result;
    }catch(cause:unknown) {
      if(stale())return;
      setError(cause instanceof Error?cause.message:"Error de administración.");
      if(cause instanceof AdminCatalogError && cause.status===401)void logout();
    }finally{
      if(!stale())setBusy(false);
    }
  }
  function saved(notice:string){
    setMessage(notice);
    setEditor(null);
  }
  function refresh(){
    if(busy)return;
    setError("");setVersion(n=>n+1);
  }
  const list=products?.page===page?products.value:null;
  return (
    <div className="admin-management">
      <p className="admin-note">Área exclusiva para ADMIN. Los cambios se guardan
        en PostgreSQL mediante la API protegida, sin almacenamiento de tokens.</p>
      {error && <p role="alert" className="admin-error">{error}</p>}
      {message && <p role="status" className="admin-success">{message}</p>}
      <div className="admin-actions">
        <button className="admin-secondary" type="button" disabled={busy}
          onClick={refresh}>Actualizar datos</button>
        {!editor && <button type="button" className="admin-primary" disabled={busy}
          onClick={()=>setEditor({key:"new",selected:null})}>Nuevo producto</button>}
      </div>
      {editor && <ProductEditor key={editor.key} selected={editor.selected}
        categories={categories??[]} disabled={busy} run={run}
        onSaved={saved} onCancel={()=>setEditor(null)}/>}
      <section className="admin-panel" aria-labelledby="admin-products-title">
        <h2 id="admin-products-title">Productos del catálogo</h2>
        {!list?<p role="status">Cargando productos administrativos…</p>:(
          <>
            <p>{list.totalElements} productos, incluidos los desactivados.
              Página {list.page+1} de {Math.max(list.totalPages,1)}.</p>
            {list.items.length===0?<p>Sin productos en esta página.</p>:(
              <div className="admin-table-scroller">
                <table className="admin-table">
                  <thead><tr>
                    <th scope="col">Producto</th><th scope="col">Precio</th>
                    <th scope="col">Stock</th><th scope="col">Estado</th>
                    <th scope="col">Acciones</th>
                  </tr></thead>
                  <tbody>{list.items.map(item=>(
                    <tr key={item.id}>
                      <td><strong>{item.name}</strong><small>SKU: {item.sku}</small>
                        <small>{item.categoryName??"Sin categoría"}
                          {item.brand?" · "+item.brand:""}</small></td>
                      <td>{formatCatalogCrc(item.priceCrc)}</td>
                      <td>{item.stock}</td>
                      <td>{item.active?"Activo":"Desactivado"}</td>
                      <td><div className="admin-inline-actions">
                        <button type="button" className="admin-secondary" disabled={busy}
                          onClick={()=>setEditor({key:String(item.id),selected:item})}>
                          Editar
                        </button>
                        <button type="button" className="admin-text" disabled={busy}
                          onClick={()=>setPendingStatus(item)}>
                          {item.active?"Desactivar":"Reactivar"}
                        </button>
                      </div></td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            )}
            <nav className="admin-actions" aria-label="Paginación administrativa">
              <button className="admin-secondary" disabled={busy||page===0}
                onClick={()=>setPage(p=>p-1)}>Anterior</button>
              <button className="admin-secondary" disabled={busy||page+1>=list.totalPages}
                onClick={()=>setPage(p=>p+1)}>Siguiente</button>
            </nav>
          </>
        )}
        {pendingStatus && <div className="admin-confirm" role="group"
          aria-label="Confirmar cambio de visibilidad">
          <p>¿{pendingStatus.active?"Desactivar":"Reactivar"} «{pendingStatus.name}»?
            {pendingStatus.active?" El artículo dejará de aparecer en el catálogo público.":""}</p>
          <div className="admin-actions">
            <button type="button" className="admin-primary" disabled={busy}
              onClick={()=>void run(()=>setAdminProductActive(
                token,pendingStatus.id,!pendingStatus.active)).then(result=>{
                  if(result){saved("Estado de producto actualizado.");setPendingStatus(null);}
                })}>Confirmar</button>
            <button type="button" className="admin-secondary" disabled={busy}
              onClick={()=>setPendingStatus(null)}>Cancelar</button>
          </div>
        </div>}
      </section>
      <section className="admin-panel" aria-labelledby="admin-categories-title">
        <h2 id="admin-categories-title">Categorías</h2>
        <p className="admin-note">No se pueden eliminar categorías con productos
          vinculados, incluso desactivados.</p>
        <form className="admin-actions" onSubmit={event=>{
          event.preventDefault();
          if(!categoryName.trim()||categoryName.trim().length>100){
            setError("Indica un nombre de categoría de 1 a 100 caracteres.");return;
          }
          void run(()=>createAdminCategory(token,categoryName.trim()))
            .then(result=>{if(result){setMessage("Categoría creada.");setCategoryName("");}});
        }}>
          <label htmlFor="admin-new-category">Nueva categoría</label>
          <input id="admin-new-category" className="admin-short-input"
            maxLength={100} value={categoryName} disabled={busy}
            onChange={e=>setCategoryName(e.target.value)}/>
          <button className="admin-primary" type="submit" disabled={busy}>
            Agregar categoría
          </button>
        </form>
        {!categories?<p role="status">Consultando categorías…</p>:(
          <ul className="admin-category-list">
            {categories.map(category=>(
              <li key={category.id}>
                {editCategory?.id===category.id?(
                  <form className="admin-actions" onSubmit={event=>{
                    event.preventDefault();
                    void run(()=>renameAdminCategory(token,category.id,
                      categoryEditName.trim())).then(result=>{
                      if(result){setMessage("Categoría actualizada.");setEditCategory(null);}
                    });
                  }}>
                    <label htmlFor={"rename-"+category.id}>Nuevo nombre de categoría</label>
                    <input id={"rename-"+category.id} value={categoryEditName}
                      maxLength={100} required className="admin-short-input"
                      disabled={busy} onChange={e=>setCategoryEditName(e.target.value)}/>
                    <button type="submit" className="admin-primary"
                      disabled={busy}>Guardar</button>
                    <button type="button" className="admin-secondary"
                      disabled={busy} onClick={()=>setEditCategory(null)}>Cancelar</button>
                  </form>
                ):(
                  <div className="admin-category-row">
                    <span><strong>{category.name}</strong> <small>#{category.id}</small></span>
                    <div className="admin-inline-actions">
                      <button type="button" className="admin-secondary"
                        disabled={busy} onClick={()=>{
                          setEditCategory(category);setCategoryEditName(category.name);
                        }}>Renombrar</button>
                      <button type="button" className="admin-text" disabled={busy}
                        onClick={()=>setDeleteCategory(category)}>Eliminar</button>
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
        {deleteCategory && <div className="admin-confirm" role="group"
          aria-label="Confirmar eliminación de categoría">
          <p>¿Eliminar la categoría «{deleteCategory.name}»?
            Solo será posible si no tiene ningún producto vinculado.</p>
          <div className="admin-actions">
            <button type="button" className="admin-primary" disabled={busy}
              onClick={()=>void run(async()=>{
                await deleteAdminCategory(token,deleteCategory.id);
                return true;
              }).then(result=>{
                if(result){setMessage("Categoría eliminada.");setDeleteCategory(null);}
              })}>Confirmar eliminación</button>
            <button type="button" className="admin-secondary" disabled={busy}
              onClick={()=>setDeleteCategory(null)}>Cancelar</button>
          </div>
        </div>}
      </section>
      <p className="admin-note">El inventario y el seguimiento de pedidos
        estarán en BKL-026/046. <Link href="/productos">Abrir catálogo público</Link>.</p>
    </div>
  );
}
