#!/usr/bin/env bash
# Captura la respuesta real de cada endpoint de FeriApp en respuestas_api.txt.
#
# Uso (desde la carpeta backend, con el servidor encendido):
#   bash scripts/capturarRespuestas.sh USUARIO_ADMIN CLAVE_ADMIN
#
# Crea un comerciante de prueba, recorre los endpoints y al final lo elimina
# (con su puesto, producto e imágenes). En el archivo de salida los tokens y
# las contraseñas se reemplazan por <TOKEN> y ***, así que se puede compartir.
# Si la clave del administrador tiene comillas o barras, el JSON se rompe.

URL="${URL:-http://localhost:3000}"
ADMIN_USUARIO="$1"
ADMIN_CLAVE="$2"
SALIDA="respuestas_api.txt"
IMAGEN="prueba_api.png"

if [ -z "$ADMIN_USUARIO" ] || [ -z "$ADMIN_CLAVE" ]; then
  echo "Uso: bash scripts/capturarRespuestas.sh USUARIO_ADMIN CLAVE_ADMIN"
  exit 1
fi

: > "$SALIDA"
CODIGO=""
CUERPO_RESP=""
TOKEN_ADMIN=""
ID_COMERCIANTE=""

# Si el script se interrumpe a mitad de camino, borra al comerciante de prueba.
limpiar() {
  rm -f "$IMAGEN"
  if [ -n "$ID_COMERCIANTE" ] && [ -n "$TOKEN_ADMIN" ]; then
    curl -s -o /dev/null -X DELETE "$URL/api/admin/comerciantes/$ID_COMERCIANTE" \
      -H "Authorization: Bearer $TOKEN_ADMIN"
  fi
}
trap limpiar EXIT

ocultar() {
  sed -E 's/"token":"[^"]+"/"token":"<TOKEN>"/g; s/"contrasena":"[^"]*"/"contrasena":"***"/g'
}

anotar() { # titulo metodo ruta cuerpo
  {
    echo "=== $1"
    echo "$2 $3"
    if [ -n "$4" ]; then
      echo "Cuerpo enviado: $(printf '%s' "$4" | ocultar)"
    fi
    echo "HTTP $CODIGO"
    printf '%s\n' "$CUERPO_RESP" | ocultar
    echo
  } >> "$SALIDA"
  echo "  [$CODIGO] $2 $3"
}

llamar() { # titulo metodo ruta token cuerpo
  local args=(-s -w $'\n%{http_code}' -X "$2" "$URL$3")
  if [ -n "$4" ]; then args+=(-H "Authorization: Bearer $4"); fi
  if [ -n "$5" ]; then args+=(-H "Content-Type: application/json" -d "$5"); fi
  local respuesta
  respuesta=$(curl "${args[@]}")
  CODIGO=$(printf '%s\n' "$respuesta" | tail -n 1)
  CUERPO_RESP=$(printf '%s\n' "$respuesta" | sed '$d')
  anotar "$1" "$2" "$3" "$5"
}

llamar_archivo() { # titulo metodo ruta token campo
  local respuesta
  respuesta=$(curl -s -w $'\n%{http_code}' -X "$2" "$URL$3" \
    -H "Authorization: Bearer $4" -F "$5=@$IMAGEN;type=image/png")
  CODIGO=$(printf '%s\n' "$respuesta" | tail -n 1)
  CUERPO_RESP=$(printf '%s\n' "$respuesta" | sed '$d')
  anotar "$1" "$2" "$3" "(archivo $IMAGEN en el campo $5)"
}

sacar_numero() { # nombre del campo -> primer número de "campo":N
  printf '%s' "$CUERPO_RESP" | sed -nE "s/.*\"$1\":([0-9]+).*/\1/p" | head -n 1
}

sacar_token() {
  printf '%s' "$CUERPO_RESP" | sed -nE 's/.*"token":"([^"]+)".*/\1/p' | head -n 1
}

abortar() {
  echo
  echo "Se detuvo: $1"
  echo "Lo capturado hasta aquí quedó en $SALIDA (puedes subirlo para revisarlo)."
  exit 1
}

echo "Comprobando el servidor en $URL ..."
llamar "Categorías (público)" GET /api/categorias "" ""
if [ "$CODIGO" != "200" ]; then
  rm -f "$SALIDA"
  echo "El servidor no responde en $URL. Enciéndelo con: npm run desarrollo"
  exit 1
fi

echo "Consultas públicas ..."
llamar "Puestos (público)" GET /api/puestos "" ""
llamar "Puestos cercanos" GET "/api/puestos/cercanos?lat=-16.495&lng=-68.175&radio=1000" "" ""
llamar "Un puesto por id" GET /api/puestos/1 "" ""
llamar "Ubicación de un puesto" GET /api/puestos/1/ubicacion "" ""
llamar "Productos filtrados por nombre" GET "/api/productos?nombre=man" "" ""
llamar "Productos filtrados por categoría" GET "/api/productos?id_categoria=1" "" ""
llamar "Productos de un puesto" GET /api/productos/puesto/1 "" ""
llamar "Registrar una búsqueda" POST /api/busquedas "" '{"termino":"manzana","id_categoria":1}'

echo "Administrador ..."
llamar "Login del administrador" POST /api/auth/login "" \
  "{\"identificador\":\"$ADMIN_USUARIO\",\"contrasena\":\"$ADMIN_CLAVE\"}"
TOKEN_ADMIN=$(sacar_token)
if [ "${#TOKEN_ADMIN}" -lt 50 ]; then
  TOKEN_ADMIN=""
  abortar "el login del administrador falló (revisa el usuario y la clave)."
fi
llamar "Login con clave incorrecta (error 401)" POST /api/auth/login "" \
  '{"identificador":"70011001","contrasena":"incorrecta"}'

echo "Comerciante de prueba ..."
TEL="7009$((RANDOM % 9000 + 1000))"
llamar "Registro de un comerciante nuevo" POST /api/comerciantes/registro "" \
  "{\"nombre\":\"Prueba\",\"apellido\":\"Captura\",\"telefono\":\"$TEL\",\"contrasena\":\"clave456\",\"nombre_comercial\":\"Comercio de captura\",\"descripcion_comercio\":\"Creado por el script\"}"
if [ "$CODIGO" != "201" ]; then
  abortar "el registro no devolvió 201."
fi
ID_COMERCIANTE=$(sacar_numero id_comerciante)

llamar "Login de comerciante" POST /api/auth/login "" \
  "{\"identificador\":\"$TEL\",\"contrasena\":\"clave456\"}"
TOKEN_COM=$(sacar_token)
if [ "${#TOKEN_COM}" -lt 50 ]; then
  abortar "el login del comerciante falló."
fi

llamar "Perfil del comerciante SIN puesto" GET /api/comerciantes/yo "$TOKEN_COM" ""
llamar "Crear producto sin puesto (error 409)" POST /api/productos "$TOKEN_COM" \
  '{"id_categoria":6,"nombre_producto":"Producto captura","precio":4.5}'
llamar "Crear puesto fuera del área (error 400)" POST /api/puestos "$TOKEN_COM" \
  '{"nombre_puesto":"Puesto captura","latitud":-16.40,"longitud":-68.17}'
llamar "Crear puesto" POST /api/puestos "$TOKEN_COM" \
  '{"nombre_puesto":"Puesto captura","descripcion":"Puesto de prueba","latitud":-16.4950,"longitud":-68.1750}'
ID_PUESTO=$(sacar_numero id_puesto)
if [ -z "$ID_PUESTO" ]; then
  abortar "no se pudo crear el puesto."
fi
llamar "Crear un segundo puesto (error 409)" POST /api/puestos "$TOKEN_COM" \
  '{"nombre_puesto":"Otro puesto","latitud":-16.4950,"longitud":-68.1750}'
llamar "Crear producto" POST /api/productos "$TOKEN_COM" \
  '{"id_categoria":6,"nombre_producto":"Producto captura","descripcion":"Producto de prueba","precio":4.5}'
ID_PRODUCTO=$(sacar_numero id_producto)
if [ -z "$ID_PRODUCTO" ]; then
  abortar "no se pudo crear el producto."
fi

echo "Imágenes ..."
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==" | base64 -d > "$IMAGEN"
llamar_archivo "Subir imagen del producto" POST "/api/productos/$ID_PRODUCTO/imagenes" "$TOKEN_COM" imagenes
ID_IMAGEN=$(sacar_numero id_imagen)
llamar_archivo "Subir imagen del puesto" POST "/api/puestos/$ID_PUESTO/imagen" "$TOKEN_COM" imagen

echo "Perfil, búsqueda y ediciones ..."
llamar "Perfil del comerciante CON puesto, producto e imágenes" GET /api/comerciantes/yo "$TOKEN_COM" ""
llamar "Productos de su puesto (con imágenes)" GET "/api/productos/puesto/$ID_PUESTO" "" ""
llamar "Búsqueda que devuelve puesto e imágenes" GET "/api/productos?nombre=captura" "" ""
llamar "Actualizar precio y disponibilidad del producto" PUT "/api/productos/$ID_PRODUCTO" "$TOKEN_COM" \
  '{"precio":5.5,"disponible":false}'
llamar "Actualizar el puesto" PUT "/api/puestos/$ID_PUESTO" "$TOKEN_COM" \
  '{"descripcion":"Puesto editado"}'
llamar "Actualizar la ubicación del puesto" PUT "/api/puestos/$ID_PUESTO/ubicacion" "$TOKEN_COM" \
  '{"latitud":-16.4960,"longitud":-68.1760}'
llamar "Actualizar el perfil" PUT /api/comerciantes/yo "$TOKEN_COM" \
  '{"descripcion":"Perfil editado"}'
llamar "Modificar un producto ajeno (error 403)" PUT /api/productos/1 "$TOKEN_COM" '{"precio":9}'
if [ -n "$ID_IMAGEN" ]; then
  llamar "Eliminar una imagen del producto" DELETE "/api/productos/$ID_PRODUCTO/imagenes/$ID_IMAGEN" "$TOKEN_COM" ""
fi

echo "Administrador (gestión y reportes) ..."
llamar "Admin sin token (error 401)" GET /api/admin/comerciantes "" ""
llamar "Admin con token de comerciante (error 403)" GET /api/admin/comerciantes "$TOKEN_COM" ""
llamar "Admin: lista de comerciantes" GET /api/admin/comerciantes "$TOKEN_ADMIN" ""
llamar "Admin: detalle de un comerciante" GET "/api/admin/comerciantes/$ID_COMERCIANTE" "$TOKEN_ADMIN" ""
llamar "Admin: editar un comerciante" PUT "/api/admin/comerciantes/$ID_COMERCIANTE" "$TOKEN_ADMIN" \
  '{"descripcion":"Editado por el administrador"}'
llamar "Admin: desactivar un comerciante" PUT "/api/admin/comerciantes/$ID_COMERCIANTE/estado" "$TOKEN_ADMIN" \
  '{"estado":"inactivo"}'
llamar "Comerciante desactivado intenta entrar (error 403)" POST /api/auth/login "" \
  "{\"identificador\":\"$TEL\",\"contrasena\":\"clave456\"}"
llamar "Admin: reactivar un comerciante" PUT "/api/admin/comerciantes/$ID_COMERCIANTE/estado" "$TOKEN_ADMIN" \
  '{"estado":"activo"}'
llamar "Admin: reportes" GET /api/admin/reportes "$TOKEN_ADMIN" ""

echo "Limpieza ..."
llamar "Admin: eliminar al comerciante de prueba" DELETE "/api/admin/comerciantes/$ID_COMERCIANTE" "$TOKEN_ADMIN" ""
if [ "$CODIGO" = "200" ]; then
  ID_COMERCIANTE=""
fi

echo
echo "Listo. Las respuestas están en $SALIDA"
echo "Súbelo al chat. No contiene tokens ni contraseñas."
