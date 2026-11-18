package es.aula.clientesfx;
import java.sql.*;
import java.util.*;
/** SQL y particularidades del gestor quedan aislados de la interfaz gráfica. */
public final class ClienteDao implements ClienteRepository {
    private static final String SELECT = """
        SELECT id,nombre,email,(direccion).calle AS calle,
               (direccion).ciudad AS ciudad,(direccion).codigo_postal AS cp,
               telefonos,preferencias::text AS preferencias
        FROM public.cliente
        """;

    @Override
    public List<Cliente> listar(String ciudad) throws SQLException {

        /*
         * Construimos la consulta SQL partiendo de la constante SELECT.
         *
         * Si ciudad es null:
         *     No añadimos ninguna condición WHERE.
         *     Se recuperarán todos los clientes.
         *
         * Si ciudad tiene un valor:
         *     Añadimos una condición WHERE para filtrar
         *     por el atributo ciudad del tipo compuesto
         *     direccion_tipo de PostgreSQL.
         *
         * IMPORTANTE:
         * Los espacios antes de WHERE y ORDER BY son
         * necesarios para construir una consulta válida.
         */
        String sql = SELECT
                + (ciudad == null
                ? ""
                : " WHERE (direccion).ciudad = ?")
                + " ORDER BY id";

        /*
         * Creamos una lista vacía para almacenar
         * los objetos Cliente recuperados.
         *
         * Cada fila del ResultSet se convertirá
         * en un objeto Cliente.
         */
        List<Cliente> clientes = new ArrayList<>();

        /*
         * Abrimos la conexión y preparamos la consulta.
         *
         * try-with-resources cerrará automáticamente
         * el PreparedStatement y la Connection.
         */
        try (Connection con = Conexion.abrir();
             PreparedStatement ps = con.prepareStatement(sql)) {

            /*
             * Si hemos indicado una ciudad,
             * asignamos su valor al parámetro ?.
             *
             * El índice 1 identifica el primer
             * parámetro de la consulta preparada.
             */
            if (ciudad != null) {
                ps.setString(1, ciudad);
            }

            /*
             * Ejecutamos el SELECT.
             *
             * executeQuery() devuelve un ResultSet
             * con las filas recuperadas.
             */
            try (ResultSet resultado = ps.executeQuery()) {

                /*
                 * next() desplaza el cursor a la siguiente fila.
                 *
                 * Mientras existan filas, construimos
                 * un objeto Cliente por cada una.
                 */
                while (resultado.next()) {

                    /*
                     * ========================================
                     * 1. RECUPERAR EL TIPO COMPUESTO
                     * ========================================
                     *
                     * En PostgreSQL, direccion es una columna
                     * de tipo public.direccion_tipo.
                     *
                     * Este tipo contiene tres atributos:
                     * calle, ciudad y codigo_postal.
                     *
                     * La consulta SELECT debe recuperar
                     * esos atributos mediante:
                     *
                     * (direccion).calle AS calle
                     * (direccion).ciudad AS ciudad
                     * (direccion).codigo_postal AS cp
                     *
                     * Con los valores recuperados construimos
                     * un objeto Direccion de Java.
                     */
                    Direccion direccion = new Direccion(
                            resultado.getString("calle"),
                            resultado.getString("ciudad"),
                            resultado.getString("cp")
                    );

                    /*
                     * ========================================
                     * 2. RECUPERAR EL ARRAY DE POSTGRESQL
                     * ========================================
                     *
                     * La columna telefonos es de tipo text[].
                     *
                     * No podemos recuperarla directamente
                     * como una List<String>.
                     *
                     * Primero utilizamos java.sql.Array,
                     * que representa un array SQL en JDBC.
                     */
                    java.sql.Array arraySql =
                            resultado.getArray("telefonos");

                    /*
                     * Creamos una lista Java vacía.
                     *
                     * Aquí almacenaremos los teléfonos
                     * recuperados del array PostgreSQL.
                     */
                    List<String> telefonos = new ArrayList<>();

                    /*
                     * Comprobamos que el array SQL
                     * no sea null antes de utilizarlo.
                     *
                     * Aunque nuestra tabla tiene una
                     * restricción NOT NULL, esta comprobación
                     * evita un posible NullPointerException.
                     */
                    if (arraySql != null) {

                        try {

                            /*
                             * getArray() devuelve el contenido
                             * del array SQL como un Object.
                             *
                             * Como sabemos que PostgreSQL
                             * almacena text[], realizamos
                             * una conversión a String[].
                             *
                             * Ejemplo:
                             *
                             * {"600111222","986111222"}
                             *
                             * se convierte en:
                             *
                             * String[] valores = {
                             *     "600111222",
                             *     "986111222"
                             * };
                             */
                            String[] valores =
                                    (String[]) arraySql.getArray();

                            /*
                             * Arrays.asList(valores) crea una
                             * lista de tamaño fijo respaldada
                             * por el array de cadenas.
                             *
                             * Con new ArrayList<>(...)
                             * obtenemos una lista independiente
                             * cuyo tamaño podemos modificar.
                             *
                             * addAll() incorpora sus elementos
                             * a la lista telefonos.
                             */
                            telefonos.addAll(
                                    Arrays.asList(valores)
                            );

                        } finally {

                            /*
                             * Liberamos los recursos JDBC
                             * asociados al array SQL.
                             *
                             * finally garantiza que se intente
                             * liberar el recurso incluso si
                             * falla la conversión.
                             */
                            arraySql.free();
                        }
                    }

                    /*
                     * ========================================
                     * 3. RECUPERAR LAS PREFERENCIAS JSONB
                     * ========================================
                     *
                     * En PostgreSQL, preferencias es jsonb.
                     *
                     * En nuestro modelo Java lo representamos
                     * mediante una cadena String que contiene
                     * un documento JSON.
                     *
                     * La consulta SELECT debe incluir:
                     *
                     * preferencias::text AS preferencias
                     *
                     * Así recuperamos su representación
                     * textual mediante getString().
                     */
                    String preferencias =
                            resultado.getString("preferencias");

                    /*
                     * ========================================
                     * 4. CONSTRUIR EL OBJETO CLIENTE
                     * ========================================
                     *
                     * Ya tenemos todos los datos de la fila:
                     *
                     * - id: identificador del cliente.
                     * - nombre: nombre del cliente.
                     * - email: correo electrónico.
                     * - direccion: objeto Direccion.
                     * - telefonos: List<String>.
                     * - preferencias: JSON como String.
                     *
                     * Construimos un objeto Cliente
                     * utilizando su constructor.
                     */
                    Cliente cliente = new Cliente(
                            resultado.getLong("id"),
                            resultado.getString("nombre"),
                            resultado.getString("email"),
                            direccion,
                            telefonos,
                            preferencias
                    );

                    /*
                     * ========================================
                     * 5. AÑADIR EL CLIENTE A LA LISTA
                     * ========================================
                     *
                     * Incorporamos el objeto construido
                     * a la colección de resultados.
                     *
                     * En la siguiente iteración del while
                     * se construirá otro objeto Cliente.
                     */
                    clientes.add(cliente);
                }
            }
        }

        /*
         * ========================================
         * 6. DEVOLVER LOS RESULTADOS
         * ========================================
         *
         * Devolvemos la lista completa.
         *
         * Si no se recuperó ninguna fila,
         * devolveremos una lista vacía,
         * no null.
         */
        return clientes;
    }


    @Override public long insertar(Cliente cliente) throws SQLException {
        // TODO 2: construir ROW y enviar java.sql.Array
        throw new SQLException("Pendiente de implementar: insertar (paso 2)", "0A000");
    }

    @Override public boolean actualizarContacto(long id,String email,List<String> telefonos) throws SQLException {
        // TODO 3: actualizar email y enviar el array de teléfonos
        throw new SQLException("Pendiente de implementar: actualizarContacto (paso 3)", "0A000");
    }

    @Override public boolean eliminar(long id) throws SQLException {
        try (Connection c=Conexion.abrir(); PreparedStatement ps=c.prepareStatement("DELETE FROM public.cliente WHERE id=?")) {
            ps.setLong(1,id); return ps.executeUpdate()==1;
        }
    }

    @Override public void cambiarCiudad(long id,String ciudad) throws SQLException {
        // TODO 4: llamar al procedimiento con CallableStatement
        throw new SQLException("Pendiente de implementar: cambiarCiudad (paso 4)", "0A000");
    }

    @Override public boolean cambiarPreferencias(long id,String json) throws SQLException {
        // TODO 5: enviar JSON con cast a jsonb
        throw new SQLException("Pendiente de implementar: cambiarPreferencias (paso 5)", "0A000");
    }

    @Override public long total() throws SQLException {
        // TODO 6: registrar y recuperar el parámetro de salida de la función
        throw new SQLException("Pendiente de implementar: total (paso 6)", "0A000");
    }
}
