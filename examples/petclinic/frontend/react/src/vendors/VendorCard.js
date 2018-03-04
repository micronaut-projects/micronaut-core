import React from 'react'
import config from "../config";
import {Link} from "react-router-dom";

const VendorCard = ({vendor}) => <div className="card vendor-card">
  <Link to={`/pets/vendor/${vendor.name}`}>
    <img className="card-img-top"
         src={`${config.SERVER_URL}/images/${vendor.pets.length > 0 ? vendor.pets[0].image : 'missing.png'}`}
         alt={vendor.name}/>
  </Link>
  <div className="card-body">
    <h5 className="card-title">{vendor.name}</h5>
    <p className="card-text">Pets: {vendor.pets.length}</p>
    <Link to={`/pets/vendor/${vendor.name}`} className="btn btn-primary">See all Pets</Link>
  </div>
</div>;

export default VendorCard