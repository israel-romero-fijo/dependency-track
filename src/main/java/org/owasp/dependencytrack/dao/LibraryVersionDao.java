/*
 * Copyright 2013 Axway
 *
 * This file is part of OWASP Dependency-Track.
 *
 * Dependency-Track is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Dependency-Track is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Dependency-Track.
 * If not, see http://www.gnu.org/licenses/.
 */

package org.owasp.dependencytrack.dao;

import org.apache.commons.io.IOUtils;
import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.owasp.dependencytrack.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Repository
public class LibraryVersionDao {

    @Autowired
    private SessionFactory sessionFactory;

    /*
        Returns a List of all LibraryVendors available in the application along with all child objects
     */
    public List<LibraryVendor> getLibraryHierarchy() {
        List<LibraryVendor> retlist = new ArrayList<LibraryVendor>();
        Query query = sessionFactory.getCurrentSession().createQuery("FROM LibraryVendor order by vendor asc");
        for (LibraryVendor vendor : (List<LibraryVendor>) query.list()) {
            Query query2 = sessionFactory.getCurrentSession().createQuery("FROM Library where libraryVendor=:vendor order by libraryname asc");
            query2.setParameter("vendor", vendor);
            for (Library library : (List<Library>) query2.list()) {
                Query query3 = sessionFactory.getCurrentSession().createQuery("FROM LibraryVersion where library=:library order by libraryversion asc");
                query3.setParameter("library", library);
                List<LibraryVersion> versions = (List<LibraryVersion>) query3.list();
                library.setVersions(new HashSet<LibraryVersion>(versions));
                vendor.addLibrary(library);
            }
            retlist.add(vendor);
        }
        return retlist;
    }

    /*
        Returns a List of all LibraryVendors
     */
    public List<LibraryVendor> getVendors() {
        Query query = sessionFactory.getCurrentSession().createQuery("FROM Library order by libraryname asc");
        return query.list();
    }

    /*
        Returns a List of all Libraries made by the specified LibraryVendor
     */
    public List<Library> getLibraries(int id) {
        Query query = sessionFactory.getCurrentSession().createQuery("FROM Library WHERE libraryVendor=:id order by libraryname asc");
        query.setParameter("id", id);
        return query.list();
    }

    /*
        Returns a List of all LibraryVersions for the specified Library
     */
    public List<LibraryVersion> getVersions(int id) {
        Query query = sessionFactory.getCurrentSession().createQuery("FROM LibraryVersion WHERE library=:id order by libraryversion asc");
        query.setParameter("id", id);
        return query.list();
    }

     /*
        Returns a list of LibraryVersion objects that the specified ApplicationVersion has a dependency on
     */
    @SuppressWarnings("unchecked")
    public List<LibraryVersion> getDependencies(ApplicationVersion version) {
        Query query = sessionFactory.getCurrentSession().createQuery("from ApplicationDependency where applicationVersion=:version");
        query.setParameter("version", version);

        List<LibraryVersion> libvers = new ArrayList<LibraryVersion>();
        List<ApplicationDependency> deps = query.list();
        for (ApplicationDependency dep : deps) {
            libvers.add(dep.getLibraryVersion());
        }
        return libvers;
    }

    /*
        Adds a dependency between the ID of the specified ApplicationVersion and LibraryVersion
     */
    @SuppressWarnings("unchecked")
    public void addDependency(int appversionid, int libversionid) {
        Session session = sessionFactory.openSession();

        ApplicationVersion applicationVersion = (ApplicationVersion) session.load(ApplicationVersion.class, appversionid);
        LibraryVersion libraryVersion = (LibraryVersion) session.load(LibraryVersion.class, libversionid);

        session.beginTransaction();

        ApplicationDependency dependency = new ApplicationDependency();
        dependency.setApplicationVersion(applicationVersion);
        dependency.setLibraryVersion(libraryVersion);

        session.save(dependency);
        session.getTransaction().commit();
        session.close();

    }

    /*
        Deletes the dependency between the ID of the specified ApplicationVersion and LibraryVersion
     */
    @SuppressWarnings("unchecked")
    public void deleteDependency(int appversionid, int libversionid) {
        Session session = sessionFactory.openSession();
        session.beginTransaction();

        Query query = session.createQuery("from ApplicationVersion AS appver where " +
                "appver.id=:appversionid");
        query.setParameter("appversionid", appversionid);

        ApplicationVersion applicationVersion = (ApplicationVersion) query.list().get(0);

        query = session.createQuery("from LibraryVersion AS libver where " +
                "libver.id=:libversionid");
        query.setParameter("libversionid", libversionid);

        LibraryVersion libraryVersion = (LibraryVersion) query.list().get(0);

        query = session.createQuery("from ApplicationDependency AS appdep where " +
                "appdep.libraryVersion=:libraryVersion and appdep.applicationVersion=:applicationVersion");
        query.setParameter("libraryVersion", libraryVersion);
        query.setParameter("applicationVersion", applicationVersion);

        ApplicationDependency applicationDependency = (ApplicationDependency) query.list().get(0);

        session.delete(applicationDependency);

        session.getTransaction().commit();
        session.close();


    }


    public void updateLibrary(int vendorid, int licenseid, int libraryid, int libraryversionid,
                              String libraryname, String libraryversion, String vendor,
                              String license, MultipartFile file, String language, int secuniaID) {

        InputStream licenseInputStream = null;
        try {
            Query query = sessionFactory.getCurrentSession().createQuery(
                    "update LibraryVendor set vendor=:vendor "
                            + "where id=:vendorid");

            query.setParameter("vendorid", vendorid);
            query.setParameter("vendor", vendor);
            int result = query.executeUpdate();

            query = sessionFactory.getCurrentSession().createQuery(
                    "from LibraryVendor "
                            + "where id=:vendorid");
            query.setParameter("vendorid", vendorid);

            LibraryVendor libraryVendor = (LibraryVendor) query.list().get(0);


            Blob blob;

            licenseInputStream = file.getInputStream();
            blob = Hibernate.createBlob(licenseInputStream);

            if (file.isEmpty()) {

                query = sessionFactory.getCurrentSession().createQuery(
                        "update License set licensename=:lname "
                                + "where id=:licenseid");

                query.setParameter("licenseid", licenseid);
                query.setParameter("lname", license);

                result = query.executeUpdate();

            } else {
                query = sessionFactory.getCurrentSession().createQuery(
                        "update License set licensename=:lname,"
                                + "text=:blobfile," + "filename=:filename,"
                                + "contenttype=:contenttype "
                                + "where id=:licenseid");

                query.setParameter("licenseid", licenseid);
                query.setParameter("lname", license);
                query.setParameter("blobfile", blob);
                query.setParameter("filename", file.getOriginalFilename());
                query.setParameter("contenttype", file.getContentType());

                result = query.executeUpdate();
            }


            query = sessionFactory.getCurrentSession().createQuery(
                    "from License "
                            + "where id=:licenseid");
            query.setParameter("licenseid", licenseid);
            License licenses = (License) query.list().get(0);

            query = sessionFactory.getCurrentSession().createQuery(
                    "update Library set libraryname=:libraryname,"
                            + "license=:licenses,"
                            + "libraryVendor=:libraryVendor,"
                            + "language=:language " + "where id=:libraryid");

            query.setParameter("libraryname", libraryname);
            query.setParameter("licenses", licenses);

            query.setParameter("libraryVendor", libraryVendor);
            query.setParameter("language", language);
            query.setParameter("libraryid", libraryid);

            result = query.executeUpdate();


            query = sessionFactory.getCurrentSession().createQuery(
                    "from Library "
                            + "where id=:libraryid");
            query.setParameter("libraryid", libraryid);
            Library library = (Library) query.list().get(0);


            query = sessionFactory.getCurrentSession().createQuery(
                    "update LibraryVersion set libraryversion=:libraryversion," + "secunia=:secunia,"
                            + "library=:library" + " where id=:libverid");

            query.setParameter("libraryversion", libraryversion);
            query.setParameter("library", library);
            query.setParameter("secunia", secuniaID);
            query.setParameter("libverid", libraryversionid);

            result = query.executeUpdate();

        } catch (Exception e) {

        } finally {
            IOUtils.closeQuietly(licenseInputStream);
        }
    }

    public void removeLibrary(int id) {
        Query querylib = sessionFactory.getCurrentSession().createQuery(
                "from LibraryVersion " + "where id=:libraryVersion");

        querylib.setParameter("libraryVersion", id);

        LibraryVersion version = (LibraryVersion) querylib.list().get(0);


        int libid = ((LibraryVersion) querylib.list().get(0)).getLibrary().getId();


        Query query = sessionFactory.getCurrentSession().createQuery(
                "from ApplicationDependency " + "where libraryVersion=:libraryVersion");

        query.setParameter("libraryVersion", version);
        List<ApplicationDependency> applicationDependency;


        if (!query.list().isEmpty()) {

            applicationDependency = query.list();
            for (ApplicationDependency dependency : applicationDependency) {
                sessionFactory.getCurrentSession().delete(dependency);
            }
            sessionFactory.getCurrentSession().delete(version);
        } else if (null != version) {
            sessionFactory.getCurrentSession().delete(version);
        }

        query = sessionFactory.getCurrentSession().createQuery(
                "from LibraryVersion " + "where library.id=:id");
        query.setParameter("id", libid);

        System.out.println("number of versions " + query.list().size());

        if (query.list().isEmpty()) {
            Library lib = (Library) sessionFactory.getCurrentSession().load(
                    Library.class, id);
            sessionFactory.getCurrentSession().delete(lib);
        }
    }

    @SuppressWarnings("unchecked")
    public List<License> listLicense(Integer id) {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "from License " + "where id=:licid");

        query.setParameter("licid", id);

        return query.list();

    }

    @SuppressWarnings("unchecked")
    public List<LibraryVersion> allLibrary() {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "from LibraryVersion ");


        return query.list();

    }

    @SuppressWarnings("unchecked")
    public List<Library> uniqueLibrary() {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "select distinct lib from Library as lib");

        return query.list();

    }


    @SuppressWarnings("unchecked")
    public List<License> uniqueLicense() {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "select distinct lic from License as lic");

        return query.list();

    }

    @SuppressWarnings("unchecked")
    public List<LibraryVendor> uniqueVendor() {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "select distinct lic from LibraryVendor as lic");

        return query.list();

    }

    @SuppressWarnings("unchecked")
    public List<String> uniqueLang() {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "select distinct lib.language from Library as lib");
        return query.list();

    }

    @SuppressWarnings("unchecked")
    public List<String> uniqueVer() {
        Query query = sessionFactory.getCurrentSession().createQuery(
                "select distinct libver.libraryversion from LibraryVersion as libver");
        return query.list();

    }

    public void addLibraries(String libraryname, String libraryversion, String vendor, String license, MultipartFile file, String language, int secuniaID) {
        LibraryVendor libraryVendor;
        License licenses;
        Library library;
        Session session = sessionFactory.openSession();
        session.beginTransaction();

        Query query = session.createQuery("from LibraryVendor where upper(vendor) =upper(:vendor) ");
        query.setParameter("vendor", vendor);

        if (query.list().isEmpty()) {
            libraryVendor = new LibraryVendor();
            libraryVendor.setVendor(vendor);
            session.save(libraryVendor);
        } else {
            libraryVendor = (LibraryVendor) query.list().get(0);
        }

        query = session.createQuery("from License where upper(licensename) =upper(:license) ");
        query.setParameter("license", license);


        if (query.list().isEmpty()) {
            licenses = new License();

            InputStream licenseInputStream = null;
            try {
                licenseInputStream = file.getInputStream();
                Blob blob = Hibernate.createBlob(licenseInputStream);

                licenses.setFilename(file.getOriginalFilename());
                licenses.setContenttype(file.getContentType());
                licenses.setLicensename(license);
                licenses.setText(blob);
                session.save(licenses);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                IOUtils.closeQuietly(licenseInputStream);
            }

        } else {
            licenses = (License) query.list().get(0);
        }

        query = session.createQuery("from Library as lib where upper(lib.libraryname) =upper(:libraryname) and lib.libraryVendor=:vendor ");
        query.setParameter("libraryname", libraryname);
        query.setParameter("vendor", libraryVendor);

        if (query.list().isEmpty()) {
            library = new Library();
            library.setLibraryname(libraryname);
            library.setLibraryVendor(libraryVendor);
            library.setLicense(licenses);

            library.setLanguage(language);
            session.save(library);
        } else {
            library = (Library) query.list().get(0);
        }

        query = session.createQuery("from LibraryVersion as libver where libver.library =:library and libver.library.libraryVendor=:vendor and libver.libraryversion =:libver ");
        query.setParameter("library", library);
        query.setParameter("vendor", libraryVendor);
        query.setParameter("libver", libraryversion);

        if (query.list().isEmpty())
        {
        LibraryVersion libVersion = new LibraryVersion();
        libVersion.setLibrary(library);
        libVersion.setLibraryversion(libraryversion);
        libVersion.setSecunia(secuniaID);
        session.save(libVersion);
        }

        session.getTransaction().commit();
        session.close();

    }
}